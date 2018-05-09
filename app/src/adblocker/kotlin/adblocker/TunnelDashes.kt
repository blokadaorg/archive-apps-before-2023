package adblocker

import android.content.Context
import com.github.salomonbrys.kodein.instance
import core.Dash
import core.Filters
import core.Tunnel
import core.getBrandedString
import gs.environment.inject
import kotlinx.coroutines.experimental.android.UI
import org.blokada.R

val DASH_ID_HOSTS_COUNT = "tunnel_hosts"

class TunnelDashCountDropped(
        val ctx: Context,
        val t: Tunnel = ctx.inject().instance()
) : Dash("tunnel_drop",
        R.drawable.ic_block,
        ctx.getString(R.string.tunnel_dropped_count_desc),
        onLongClick = { t.tunnelDropCount %= 0; true }
) {

    init {
        text = getBlockedString(0)
        t.tunnelDropCount.onChange(UI) {
            text = getBlockedString(t.tunnelDropCount())
        }
    }

    private fun getBlockedString(blocked: Int): String {
        return ctx.resources.getString(R.string.tunnel_dropped_count, blocked)
    }
}

class TunnelDashHostsCount(
        val ctx: Context,
        val s: Filters = ctx.inject().instance()
) : Dash(DASH_ID_HOSTS_COUNT,
        R.drawable.ic_counter,
        ctx.getBrandedString(R.string.tunnel_hosts_desc),
        onClick = { s.filters.refresh(); true }
) {

    init {
        text = getCountString(0)
        s.filtersCompiled.onChange(UI) {
            text = getCountString(s.filtersCompiled().size)
        }
    }

    private fun getCountString(count: Int): String {
        return ctx.resources.getString(R.string.tunnel_hosts_count, count)
    }
}

