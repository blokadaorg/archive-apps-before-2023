package core

import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class BlacklistDashboardSection(val ktx: AndroidKontext) : LayoutViewBinder(R.layout.vblistview),
    ListSection, Scrollable {

    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        filters.filter { !it.whitelist && !it.hidden && it.source.id == "single" }.map {
            FilterVB(it, ktx, onTap = slotMutex.openOneAtATime)
        }.apply { view?.set(listOf(NewFilterVB(ktx)) + this) }
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

    override fun setOnScroll(onScrollDown: () -> Unit, onScrollUp: () -> Unit, onScrollStopped: () -> Unit) = Unit

    override fun getScrollableView() = view!!

    override fun selectNext() { view?.selectNext() }
    override fun selectPrevious() { view?.selectPrevious() }
    override fun unselect() { view?.unselect() }

    private var listener: (SlotVB?) -> Unit = {}
    override fun setOnSelected(listener: (item: SlotVB?) -> Unit) {
        this.listener = listener
        view?.setOnSelected(listener)
    }

    override fun scrollToSelected() {
        view?.scrollToSelected()
    }
}
