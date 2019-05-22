package tunnel

import android.system.ErrnoException
import android.system.OsConstants
import com.cloudflare.app.boringtun.BoringTunJNI
import com.github.michaelbull.result.mapError
import core.Kontext
import core.Result
import core.ktx
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.SOARecord
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*

internal class BoringProxy(
        private val dnsServers: List<InetSocketAddress>,
        private val blockade: Blockade,
        private val loopback: Queue<ByteArray>,
        var forward: (Kontext, DatagramPacket) -> Unit = { _, _ -> },
        private val denyResponse: SOARecord = SOARecord(Name("org.blokada.invalid."), DClass.IN,
                5L, Name("org.blokada.invalid."), Name("org.blokada.invalid."), 0, 0, 0, 0, 5)
) {
    var tunnel: Long? = null
    val dest = ByteBuffer.allocateDirect(65535)
    val op = ByteBuffer.allocateDirect(8)
    val secretBase = "1mZwVvLTteJvp+mlz27swbnuq4vomTceEjQh0ZcrWxU="
    val publicBase = "ttZo7et1J9HaB/qoCbgvP+XkuKS3DE/IdsUQxOIvc1o="
    val publicPeerBase = "aWgVkVE22ybHrPTP5d9fPOKI6dArQykGsVzb+T1aJA4="
    val accountId = "tbjjqfkjpveo"
    val vip = "10.143.0.2"

    val datagram = ByteArray(65535)
    val empty = ByteArray(65535)

    fun fromDevice(ktx: Kontext, packetBytes: ByteArray) {
        val ktx = "boringtun".ktx()
        if (tunnel == null) {
            ktx.v("loading boringtun and generating keys")
            System.loadLibrary("boringtun")
//            val secret = BoringTunJNI.x25519_secret_key()
//            val public_key = BoringTunJNI.x25519_public_key(secret)
//
//            val secret_string = BoringTunJNI.x25519_key_to_hex(secret)
//            val public_string = BoringTunJNI.x25519_key_to_hex(public_key)

//            val secret_string_base = BoringTunJNI.x25519_key_to_base64(secret)
//            val public_string_base = BoringTunJNI.x25519_key_to_base64(public_key)

//            ktx.v("secret: " + secret_string)
//            ktx.v("public: " + public_string)
//            ktx.v("secret_base: " + secret_string_base)
//            ktx.v("public_base: " + public_string_base)
            ktx.v("creating boringtun tunnel")
            tunnel = BoringTunJNI.new_tunnel(secretBase, publicPeerBase)
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

        ktx.emit(Events.REQUEST, Request("packet", blocked = false))
    }

    fun toDevice(ktx: Kontext, response: ByteArray, length: Int) {
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
