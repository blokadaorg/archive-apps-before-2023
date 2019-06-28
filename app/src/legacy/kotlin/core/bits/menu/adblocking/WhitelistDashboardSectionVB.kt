package core.bits.menu.adblocking

import core.AndroidKontext
import core.Resource
import core.VBListView
import core.bits.FilterVB
import core.bits.NewFilterVB
import core.bits.menu.MenuItemVB
import core.res
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class WhitelistDashboardSectionVB(
        val ktx: AndroidKontext,
        override val name: Resource = R.string.panel_section_ads_whitelist.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        filters.filter { it.whitelist && !it.hidden && it.source.id != "app" }.map {
            FilterVB(it, ktx, onTap = slotMutex.openOneAtATime)
        }.apply { view?.set(listOf(NewFilterVB(ktx, whitelist = true, nameResId = R.string.slot_new_filter_whitelist)) + this) }
        Unit
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        ktx.on(Events.FILTERS_CHANGED, updateApps)
    }
}

fun createWhitelistMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = R.string.panel_section_ads_whitelist.res(),
            icon = R.drawable.ic_verified.res(),
            opens = WhitelistDashboardSectionVB(ktx)
    )
}
