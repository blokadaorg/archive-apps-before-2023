package core

import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class WhitelistDashboardSectionVB(val ktx: AndroidKontext) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        filters.filter { it.whitelist && !it.hidden && it.source.id != "app" }.map {
            FilterVB(it, ktx, onTap = slotMutex.openOneAtATime)
        }.apply { view?.set(this) }
        Unit
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        ktx.on(Events.FILTERS_CHANGED, updateApps)
    }

    override fun detach(view: View) {
        slotMutex.detach()
        ktx.cancel(Events.FILTERS_CHANGED, updateApps)
    }

}
