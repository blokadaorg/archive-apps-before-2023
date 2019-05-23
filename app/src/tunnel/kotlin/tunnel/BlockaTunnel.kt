package tunnel

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import core.Kontext
import core.Result
import org.pcap4j.packet.factory.PacketFactoryPropertiesLoader
import org.pcap4j.util.PropertiesLoader
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import kotlin.math.min


internal class BlockaTunnel(
        private var proxy: BlockaProxy,
        private val config: TunnelConfig,
        private val blockaConfig: BlockaConfig,
        private val loopback: Queue<ByteArray> = LinkedList(),
        private val doCreateSocket: () -> DatagramSocket
): Tunnel {

    private var device: FileDescriptor? = null
    private var error: FileDescriptor? = null
    private var gatewaySocket: DatagramSocket? = null

    private val cooldownTtl = 300L
    private val cooldownMax = 3000L
    private var cooldownCounter = 0
    private var epermCounter = 0

    private var packetBuffer = ByteArray(32767)
    private var datagramBuffer = ByteArray(32767)

    private var lastTickMs = 0L
    private val tickIntervalMs = 100

    fun openGatewaySocket(ktx: Kontext) {
        gatewaySocket?.close()
        gatewaySocket = doCreateSocket()
        gatewaySocket?.connect(InetAddress.getByName(blockaConfig.gatewayIp), blockaConfig.gatewayPort)
        ktx.v("connect to gateway ip: ${blockaConfig.gatewayIp}")
        proxy.forward = { ktx, udp ->
            Result.of {
                gatewaySocket!!.send(udp)
            }.mapError { ex ->
                ktx.w("failed sending gateway udp", ex.message ?: "")
                val cause = ex.cause
                if (cause is ErrnoException && cause.errno == OsConstants.EBADF) {
                    throw ex
                } else if (cause is ErrnoException && cause.errno == OsConstants.EPERM) throw ex
            }
        }
    }

    override fun run(ktx: Kontext, tunnel: FileDescriptor) {
        ktx.v("running boring tunnel thread", this)

        val input = FileInputStream(tunnel)
        val output = FileOutputStream(tunnel)

        try {
            val errors = setupErrorsPipe()
            val device = setupDevicePipe(input)

            openGatewaySocket(ktx)

            val polls = setupPolls(ktx, errors, device)

            while (true) {
                if (threadInterrupted()) throw InterruptedException()

                if (loopback.isNotEmpty()) {
                    device.listenFor(OsConstants.POLLIN or OsConstants.POLLOUT)
                } else device.listenFor(OsConstants.POLLIN)

                tick()

                poll(ktx, polls)
                fromGatewayToProxy(ktx, polls)
                fromLoopbackToDevice(ktx, device, output)
                fromDeviceToProxy(ktx, device, input, packetBuffer)
                cleanup()
            }
        } catch (ex: InterruptedException) {
            ktx.v("tunnel thread interrupted", this, ex.toString())
            Thread.currentThread().interrupt()
            throw ex
        } catch (ex: Exception) {
            val cause = ex.cause
            if (cause is ErrnoException && cause.errno == OsConstants.EPERM) {
                if (++epermCounter >= 3 && config.powersave) {
                    ktx.emit(Events.TUNNEL_POWER_SAVING)
                    epermCounter = 0
                }
            } else {
                epermCounter = 0
                ktx.e("failed tunnel thread", this, ex)
            }
            throw ex
        } finally {
            ktx.v("cleaning up resources", this)
            Result.of { Os.close(error) }
            Result.of { input.close() }
            Result.of { output.close() }
        }
    }

    override fun runWithRetry(ktx: Kontext, tunnel: FileDescriptor) {
        var interrupted = false
        do {
            Result.of { run(ktx, tunnel) }.mapError {
                if (it is InterruptedException || threadInterrupted()) interrupted = true
                else {
                    val cooldown = min(cooldownTtl * cooldownCounter++, cooldownMax)
                    ktx.e("tunnel thread error, will restart after $cooldown ms", this, it.toString())
                    Result.of { Thread.sleep(cooldown) }.mapError {
                        if (it is InterruptedException || threadInterrupted()) interrupted = true
                    }
                }
            }
        } while (!interrupted)
        ktx.v("tunnel thread shutdown", this)
    }

    override fun stop(ktx: Kontext) {
        ktx.v("stopping poll, if any")
        Result.of { Os.close(error) }
        Result.of {
            ktx.v("closing gateway socket on stop")
            gatewaySocket?.close()
        }
        gatewaySocket = null
        error = null
    }

    private fun setupErrorsPipe() = {
        val pipe = Os.pipe()
        error = pipe[0]
        val errors = StructPollfd()
        errors.fd = error
        errors.listenFor(OsConstants.POLLHUP or OsConstants.POLLERR)
        errors
    }()

    private fun setupDevicePipe(input: FileInputStream) = {
        this.device = input.fd
        val device = StructPollfd()
        device.fd = input.fd
        device
    }()

    private fun setupPolls(ktx: Kontext, errors: StructPollfd, device: StructPollfd) = {
        val polls = arrayOfNulls<StructPollfd>(3) as Array<StructPollfd>
        polls[0] = errors
        polls[1] = device

        val gateway = StructPollfd()
        gateway.fd = ParcelFileDescriptor.fromDatagramSocket(gatewaySocket).fileDescriptor
        gateway.listenFor(OsConstants.POLLIN)
        polls[2] = gateway

        polls
    }()

    private fun poll(ktx: Kontext, polls: Array<StructPollfd>) {
        while (true) {
            try {
                val result = Os.poll(polls, -1)
                if (result == 0) return
                if (polls[0].revents.toInt() != 0) throw InterruptedException("poll interrupted")
                break
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) continue
                throw e
            }
        }
    }

    private fun fromGatewayToProxy(ktx: Kontext, polls: Array<StructPollfd>) {
        if (polls[2].isEvent(OsConstants.POLLIN)) {
            val responsePacket = DatagramPacket(datagramBuffer, datagramBuffer.size)
            Result.of {
                gatewaySocket?.receive(responsePacket)
                proxy.toDevice(ktx, datagramBuffer, responsePacket.length)
            }.onFailure { ktx.w("failed receiving gateway socket", it) }
        }
    }

    private fun fromLoopbackToDevice(ktx: Kontext, device: StructPollfd, output: OutputStream) {
        if (device.isEvent(OsConstants.POLLOUT)) {
            output.write(loopback.poll())
        }
    }

    private fun fromDeviceToProxy(ktx: Kontext, device: StructPollfd, input: InputStream,
                                  buffer: ByteArray) {
        if (device.isEvent(OsConstants.POLLIN)) {
            val length = input.read(buffer)
            if (length > 0) {
                // TODO: nocopy
                val readPacket = Arrays.copyOfRange(buffer, 0, length)
                proxy.fromDevice(ktx, readPacket)
            }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (now > (lastTickMs + tickIntervalMs)) {
//            "boringtun:tick2".ktx().v("tick after ${now - lastTickMs}ms")
            lastTickMs = now
            proxy.tick()
        }
    }

    private fun threadInterrupted() = (Thread.interrupted() || this.error == null)

    private var count = 0
    private fun cleanup() {
        if (++count % 1024 == 0) {
            try {
                val l = PacketFactoryPropertiesLoader.getInstance()
                val field = l.javaClass.getDeclaredField("loader")
                field.isAccessible = true
                val loader = field.get(l) as PropertiesLoader
                loader.clearCache()
            } catch (e: Exception) {
            }
        }
    }

}

private fun StructPollfd.listenFor(events: Int) {
    this.events = events.toShort()
}

private fun StructPollfd.isEvent(event: Int): Boolean {
    return this.revents.toInt() and event != 0
}


