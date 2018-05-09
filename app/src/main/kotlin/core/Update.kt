package core

import android.content.Context
import com.github.salomonbrys.kodein.*
import gs.environment.Environment
import gs.environment.Journal
import gs.environment.Time
import gs.environment.Worker
import gs.property.Property
import gs.property.Repo
import kotlinx.coroutines.experimental.android.UI
import notification.displayNotificationForUpdate
import org.blokada.R
import update.AUpdateDownloader
import update.UpdateCoordinator
import update.isUpdate

abstract class Update {
    abstract val lastSeenUpdateMillis: Property<Long>
}

class UpdateImpl (
        w: Worker,
        xx: Environment,
        val ctx: Context = xx().instance()
) : Update() {

    override val lastSeenUpdateMillis = Property.ofPersisted({0L}, APrefsPersistence(ctx, "lastSeenUpdate"))
}

fun newUpdateModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Update>() with singleton {
            UpdateImpl(w = with("gscore").instance(), xx = lazy)
        }
        bind<UpdateCoordinator>() with singleton {
            UpdateCoordinator(xx = lazy, downloader = AUpdateDownloader(ctx = instance()))
        }
        onReady {
            val s: Filters = instance()
            val t: Tunnel = instance()
            val ui: UiState = instance()
            val u: Update = instance()
            val repo: Repo = instance()

            // Check for update periodically
            t.tunnelState.onChange {
                if (t.tunnelState() == TunnelState.ACTIVE) {
                    // This "pokes" the cache and refreshes if needed
                    repo.content.refresh(recheck = true)
                    s.filters.refresh(recheck = true)
                }
            }

            // Display an info message when update is available
            repo.content.onChange(UI) {
                if (isUpdate(ctx, repo.content().newestVersionCode)) {
                    ui.infoQueue %= ui.infoQueue() + Info(InfoType.CUSTOM, R.string.update_infotext)
                    u.lastSeenUpdateMillis.refresh()
                }
            }

            // Display notifications for updates
            u.lastSeenUpdateMillis.onChange(UI) {
                val content = repo.content()
                val last = u.lastSeenUpdateMillis()
                val cooldown = 86400 * 1000L
                val env: Time = instance()
                val j: Journal = instance()

                if (isUpdate(ctx, content.newestVersionCode) && canShowNotification(last, env, cooldown)) {
                    displayNotificationForUpdate(ctx, content.newestVersionName)
                    u.lastSeenUpdateMillis %= env.now()
                }
            }


        }
    }
}

internal fun canShowNotification(last: Long, env: Time, cooldownMillis: Long): Boolean {
    return last + cooldownMillis < env.now()
}
