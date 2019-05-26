package tunnel

import android.system.ErrnoException
import android.system.OsConstants
import com.cloudflare.app.boringtun.BoringTunJNI
import com.github.michaelbull.result.mapError
import core.Kontext
import core.Result
import core.ktx
import org.pcap4j.packet.*
import org.xbill.DNS.*
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.Inet6Address
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

    private fun interceptDns(ktx: Kontext, packetBytes: ByteArray): Boolean {
        val originEnvelope = try {
            IpSelector.newPacket(packetBytes, 0, packetBytes.size) as IpPacket
        } catch (e: Exception) {
            return false
        }

        if (originEnvelope.payload !is UdpPacket) return false

        val udp = originEnvelope.payload as UdpPacket
        if (udp.payload == null) {
            // Some apps use empty UDP packets for something good
            return false
        }

        val udpRaw = udp.payload.rawData
        val dnsMessage = try {
            Message(udpRaw)
        } catch (e: IOException) {
            return false
        }
        if (dnsMessage.question == null) return false

        val host = dnsMessage.question.name.toString(true).toLowerCase(Locale.ENGLISH)
        return if (blockade.allowed(host) || !blockade.denied(host)) {
            ktx.emit(Events.REQUEST, Request(host))
            false
        } else {
            dnsMessage.header.setFlag(Flags.QR.toInt())
            dnsMessage.header.rcode = Rcode.NOERROR
            dnsMessage.addRecord(denyResponse, Section.AUTHORITY)
            toDeviceFakeDnsResponse(ktx, dnsMessage.toWire(), -1, originEnvelope)
            ktx.emit(Events.REQUEST, Request(host, blocked = true))
            true
        }
    }

    override fun fromDevice(ktx: Kontext, packetBytes: ByteArray) {
        if (interceptDns(ktx, packetBytes)) return

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

    private fun toDeviceFakeDnsResponse(ktx: Kontext, response: ByteArray, length: Int, originEnvelope: Packet?) {
        originEnvelope as IpPacket
        val udp = originEnvelope.payload as UdpPacket
        val udpResponse = UdpPacket.Builder(udp)
                .srcAddr(originEnvelope.header.dstAddr)
                .dstAddr(originEnvelope.header.srcAddr)
                .srcPort(udp.header.dstPort)
                .dstPort(udp.header.srcPort)
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(UnknownPacket.Builder().rawData(response))

        val envelope: IpPacket
        if (originEnvelope is IpV4Packet) {
            envelope = IpV4Packet.Builder(originEnvelope)
                    .srcAddr(originEnvelope.header.dstAddr as Inet4Address)
                    .dstAddr(originEnvelope.header.srcAddr as Inet4Address)
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(udpResponse)
                    .build()
        } else {
            envelope = IpV6Packet.Builder(originEnvelope as IpV6Packet)
                    .srcAddr(originEnvelope.header.dstAddr as Inet6Address)
                    .dstAddr(originEnvelope.header.srcAddr as Inet6Address)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(udpResponse)
                    .build()
        }
        loopback.add(envelope.rawData)
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
