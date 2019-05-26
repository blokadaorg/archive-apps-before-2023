package tunnel

import android.system.ErrnoException
import android.system.OsConstants
import com.cloudflare.app.boringtun.BoringTunJNI
import com.github.michaelbull.result.mapError
import core.Kontext
import core.Result
import core.ktx
import org.pcap4j.packet.Packet
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.SOARecord
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*

interface Proxy {
    fun fromDevice(ktx: Kontext, packetBytes: ByteArray)
    fun toDevice(ktx: Kontext, response: ByteArray, length: Int, originEnvelope: Packet? = null)
}

internal class BlockaProxy(
        private val dnsServers: List<InetSocketAddress>,
        private val blockade: Blockade,
        private val loopback: Queue<ByteArray>,
        private val config: BlockaConfig,
        var forward: (Kontext, DatagramPacket) -> Unit = { _, _ -> },
        private val denyResponse: SOARecord = SOARecord(Name("org.blokada.invalid."), DClass.IN,
                5L, Name("org.blokada.invalid."), Name("org.blokada.invalid."), 0, 0, 0, 0, 5)
) : Proxy {
    var tunnel: Long? = null
    val dest = ByteBuffer.allocateDirect(65535)
    val op = ByteBuffer.allocateDirect(8)

    val datagram = ByteArray(65535)
    val empty = ByteArray(65535)

    override fun fromDevice(ktx: Kontext, packetBytes: ByteArray) {
        val ktx = "boringtun".ktx()
        if (tunnel == null) {
            ktx.v("creating boringtun tunnel", config.gatewayId)
            tunnel = BoringTunJNI.new_tunnel(config.privateKey, config.gatewayId)
        }

        var written = 0
        do {
//            ktx.v("tun $tunnel, wireguard write")
            dest.rewind()
            op.rewind()
            val resp = BoringTunJNI.wireguard_write(tunnel!!, if (written == 0) packetBytes else empty,
                    packetBytes.size, dest, dest.capacity(), op)
            op.rewind()
            written++
            when (op[0].toInt()) {
                BoringTunJNI.WRITE_TO_NETWORK -> {
//                    ktx.v("writing to network (size: $resp)")
                    Result.of {
                        val array = ByteArray(resp) // todo: no copy
                        dest.get(array, 0, resp)
                        val udp = DatagramPacket(array, 0, resp)
                        forward(ktx, udp)
//                        ktx.v("sent")
                    }.mapError { ex ->
                        ktx.w("failed sending to gateway", ex.message ?: "")
                        val cause = ex.cause
                        if (cause is ErrnoException && cause.errno == OsConstants.EBADF) throw ex
                    }
//                    ktx.v("writing done")
                }
                BoringTunJNI.WIREGUARD_ERROR -> {
                    ktx.e("wireguard error: $resp")
                }
                BoringTunJNI.WIREGUARD_DONE -> {
//                    ktx.v("done")
                }
                else -> {
                    ktx.w("wireguard write unknown response: ${op[0].toInt()}")
                }
            }
        } while (resp == BoringTunJNI.WRITE_TO_NETWORK)
    }

    override fun toDevice(ktx: Kontext, response: ByteArray, length: Int, originEnvelope: Packet?) {
        val ktx = "boringtun".ktx()
        var written = 0
        do {
//            ktx.v("tun $tunnel, reading packet")
            op.rewind()
            dest.rewind()
            val resp = BoringTunJNI.wireguard_read(tunnel!!, if (written == 0) response else empty,
                    length, dest, dest.capacity(), op)
            written++
            op.rewind()
            when (op[0].toInt()) {
                BoringTunJNI.WRITE_TO_NETWORK -> {
//                    ktx.v("read: writing to network")
                    Result.of {
                        val array = ByteArray(resp) // todo: no copy
                        dest.get(array, 0, resp)
                        val udp = DatagramPacket(array, 0, resp)
                        forward(ktx, udp)
//                        ktx.v("timer: sent")
                    }.mapError { ex ->
                        ktx.w("failed sending to gateway", ex.message ?: "")
                        val cause = ex.cause
                        if (cause is ErrnoException && cause.errno == OsConstants.EBADF) throw ex
                    }
                }
                BoringTunJNI.WIREGUARD_ERROR -> {
                    ktx.e("read: wireguard error: $resp, for response size: $length")
                }
                BoringTunJNI.WIREGUARD_DONE -> {
//                    ktx.v("read: done")
                }
                BoringTunJNI.WRITE_TO_TUNNEL_IPV4, BoringTunJNI.WRITE_TO_TUNNEL_IPV6 -> {
//                    ktx.v("read: writing to tunnel")
                    val array = ByteArray(resp) // todo: no copy
                    dest.get(array, 0, resp)
                    loopback.add(array)
                }
                else -> {
                    ktx.w("read: wireguard unknown response: ${op[0].toInt()}")
                }
            }
        } while (resp == BoringTunJNI.WRITE_TO_NETWORK)
//        loopback(ktx, envelope.rawData)
    }

    fun tick() {
        val ktx = "boringtun:tick".ktx()
        if (tunnel == null) {
            return
        }

        dest.rewind()
        op.rewind()
        val resp = BoringTunJNI.wireguard_tick(tunnel!!, dest, dest.capacity(), op)
        op.rewind()
        when (op[0].toInt()) {
            BoringTunJNI.WRITE_TO_NETWORK -> {
                ktx.v("timer: writing to network")
                Result.of {
                    val array = ByteArray(resp) // todo: no copy
                    dest.get(array, 0, resp)
                    val udp = DatagramPacket(array, 0, resp)
                    forward(ktx, udp)
                }.mapError { ex ->
                    ktx.w("tick: failed sending to gateway", ex.message ?: "")
                    val cause = ex.cause
                    if (cause is ErrnoException && cause.errno == OsConstants.EBADF) throw ex
                }
            }
            BoringTunJNI.WIREGUARD_ERROR -> {
                ktx.e("wireguard error: $resp")
            }
            BoringTunJNI.WIREGUARD_DONE -> {
//                    ktx.v("done")
            }
            else -> {
                ktx.w("wireguard timer unknown response: ${op[0].toInt()}")
            }
        }
    }
}
