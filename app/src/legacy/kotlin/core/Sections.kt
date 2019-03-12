package core

import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class FiltersSectionVB(val ktx: AndroidKontext) : LayoutViewBinder(R.layout.vblistview), Scrollable {

    private var view: VBListView? = null
    private val openedView = SlotMutex()

    private val filtersUpdated = { filters: Collection<Filter> ->
        val items = filters.filter {
            !it.whitelist && !it.hidden && it.source.id != "single"
        }.sortedBy { it.priority }.map { FilterVB(it, ktx, slotMutex = openedView) }
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

    override fun setOnScroll(onScrollDown: () -> Unit, onScrollUp: () -> Unit, onScrollStopped: () -> Unit) = Unit

    override fun getScrollableView() = view!!
}

class StaticItemsListVB(
        private val items: List<SlotVB>
) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null
    private val slotMutex = SlotMutex()

    init {
        items.forEach { it.slotMutex = slotMutex }
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        view.set(items)
    }

    override fun detach(view: View) {
        slotMutex.view = null
    }
}
