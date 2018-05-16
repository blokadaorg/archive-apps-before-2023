package core2

import android.app.Activity
import android.content.Context
import android.net.VpnService
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.runBlocking

fun Context.getPermissions() = VpnService.prepare(this) == null

fun Activity.startAskTunnelPermissions(c: Pipe) = runBlocking {
    val intent = VpnService.prepare(this@startAskTunnelPermissions)
    when (intent) {
        null -> {
            c.send("askPermissions: already granted")
        }
        else -> {
            c.send("askPermissions: starting activity")
            startActivityForResult(intent, 0)
            if (tunnelPermissionsChannel.receive() != -1)
                c.send("askPermissions: rejected")
            else
                c.send("askPermissions: granted")
        }
    }
}

fun Activity.stopAskTunnelPermissions(resultCode: Int) = runBlocking {
    tunnelPermissionsChannel.send(resultCode)
}

private val tunnelPermissionsChannel = Channel<Int>()

