package tunnel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.with
import gs.environment.Worker
import gs.environment.inject
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import java.io.FileDescriptor
import java.net.DatagramSocket

/**
 * ATunnelAgent manages the state of the ATunnelService.
 */
class ATunnelAgent(val ctx: Context) {

    private val tunnelKctx by lazy { ctx.inject().with("tunnel").instance<Worker>() }

    private var events: ITunnelEvents? = null
    private var channel: Channel<ATunnelBinder?> = Channel()

    private val serviceConnection = object: ServiceConnection {
        @Synchronized override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val b = binder as ATunnelBinder
            b.events = events
            launch {
                 channel.send(b)
            }
        }

        @Synchronized override fun onServiceDisconnected(name: ComponentName?) {
            launch {
                channel.send(null)
            }
        }
    }

    fun bind(events: ITunnelEvents) = async {
        this@ATunnelAgent.events = events
        val intent = Intent(ctx, ATunnelService::class.java)
        intent.setAction(ATunnelService.BINDER_ACTION)
        when (ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE
            or Context.BIND_ABOVE_CLIENT or Context.BIND_IMPORTANT)) {
            true -> Unit
            else -> {
                channel.send(null)
            }
        }
        channel
    }

    fun unbind() {
        this.events = null
        try { ctx.unbindService(serviceConnection) } catch (e: Exception) {}
    }
}

class ATunnelBinder(
        val actions: ITunnelActions,
        var events: ITunnelEvents? = null
) : Binder()

interface ITunnelActions {
    fun turnOn(): Int
    fun turnOff()
    fun protect(socket: DatagramSocket): Boolean
    fun fd(): FileDescriptor?
}

interface ITunnelEvents {
    fun configure(builder: VpnService.Builder): Long
    fun revoked()
}
