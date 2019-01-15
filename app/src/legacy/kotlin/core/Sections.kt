package core

import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class ListsSectionVB(val ktx: AndroidKontext) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null
    private val openedView = SlotMutex()

    private val filtersUpdated = { filters: Collection<Filter> ->
        val items = filters.filter {
            !it.whitelist && !it.hidden
        }.sortedBy { it.priority }.map { FilterVB(it, ktx, openedView) }
        view?.set(listOf(NewFilterVB(ktx)) + items)
        Unit
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        ktx.on(Events.FILTERS_CHANGED, filtersUpdated)
    }

    override fun detach(view: View) {
        openedView.view = null
        ktx.cancel(Events.FILTERS_CHANGED, filtersUpdated)
    }

}

class SettingsSectionVB(val ktx: AndroidKontext) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null
    private val slotMutex = SlotMutex()

    private val items = listOf(
            DownloadListsVB(ktx, slotMutex = slotMutex),
            ListDownloadFrequencyVB(ktx, slotMutex = slotMutex),
            DownloadOnWifiVB(ktx, slotMutex = slotMutex)
    )

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        view.set(items)
    }

    override fun detach(view: View) {
        slotMutex.view = null
//        ktx.cancel(Events.FILTERS_CHANGED, filtersUpdated)
    }
}
