package notification

import android.app.IntentService
import android.content.Intent
import android.widget.Toast
import com.github.salomonbrys.kodein.instance
import core.Filter
import core.Filters
import core.LocalisedFilter
import filter.FilterSourceSingle
import gs.environment.inject
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.blokada.R

class ANotificationsWhitelistService : IntentService("notificationsWhitelist") {

    private val s by lazy { inject().instance<Filters>() }

    override fun onHandleIntent(intent: Intent) {
        val host = intent.getStringExtra("host") ?: return

        val filter = Filter(
                id = host,
                source = FilterSourceSingle(host),
                active = true,
                whitelist = true,
                localised = LocalisedFilter(host)
        )

        val existing = s.filters().firstOrNull { it == filter }
        if (existing == null) {
            s.filters %= s.filters() + filter
            s.changed %= true
        } else if (!existing.active) {
            existing.active = true
            s.uiChangeCounter %= s.uiChangeCounter() + 1
            s.changed %= true
        }

        launch(UI) {
            Toast.makeText(this@ANotificationsWhitelistService, R.string.notification_blocked_whitelist_applied, Toast.LENGTH_SHORT).show()
            hideNotification(this@ANotificationsWhitelistService)
        }
    }

}
