package tunnel

import android.app.Activity
import android.content.Context
import android.net.VpnService
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch


/**
 * ATunnelPermsUtils contains bits and pieces required to ask user for Tunnel
 * permissions using Android APIs.
 */


val tunnelPermissionsChannel = Channel<Int>()

/**
 * Initiates the procedure causing the OS to display the permission dialog.
 *
 * Returns a deferred object which will be resolved once the flow is finished.
 * If permissions are already granted, the deferred is already resolved.
 */
fun startAskTunnelPermissions(act: Activity) = async {
    val intent = VpnService.prepare(act)
    when (intent) {
        null -> Unit
        else -> {
            act.startActivityForResult(intent, 0)
            if (tunnelPermissionsChannel.receive() != -1) {
                throw Exception("tunnel permissions rejected")
            }
        }
    }
}

/**
 * Finishes the flow. Should be hooked up to onActivityResult() of Activity.
 */
fun stopAskTunnelPermissions(resultCode: Int) {
    launch {
        tunnelPermissionsChannel.send(resultCode)
    }
}

/**
 * Checks tunnel permissions and throws exception if not granted.
 */
fun checkTunnelPermissions(ctx: Context) {
    if (VpnService.prepare(ctx) != null) {
        throw Exception("no tunnel permissions")
    }
}

