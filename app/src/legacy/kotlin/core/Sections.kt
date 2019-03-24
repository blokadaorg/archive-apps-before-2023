package core

import android.view.View
import gs.presentation.LayoutViewBinder
import gs.presentation.ViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

internal class SlotMutex {

    private var openedView: SlotView? = null

    val openOneAtATime = { view: SlotView ->
        val opened = openedView
        when {
            opened == null || !opened.isUnfolded() -> {
                openedView = view
                view.unfold()
            }
            opened.isUnfolded() -> {
                opened.fold()
                view.onClose()
            }
        }
    }

    fun detach() {
        openedView = null
    }
}


class FiltersSectionVB(val ktx: AndroidKontext) : LayoutViewBinder(R.layout.vblistview), Scrollable,
    ListSection {

    private var view: VBListView? = null
    private val slotMutex = SlotMutex()
    private var listener: (ViewBinder?) -> Unit = {}

    private val filtersUpdated = { filters: Collection<Filter> ->
        val items = filters.filter {
            !it.whitelist && !it.hidden && it.source.id != "single"
        }.sortedBy { it.priority }.map { FilterVB(it, ktx, onTap = slotMutex.openOneAtATime) }
        view?.set(listOf(NewFilterVB(ktx)) + items)
        listener(null)
        Unit
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        ktx.on(Events.FILTERS_CHANGED, filtersUpdated)
    }

    override fun detach(view: View) {
        slotMutex.detach()
        ktx.cancel(Events.FILTERS_CHANGED, filtersUpdated)
    }

    override fun setOnScroll(onScrollDown: () -> Unit, onScrollUp: () -> Unit, onScrollStopped: () -> Unit) = Unit

    override fun getScrollableView() = view!!

    override fun selectNext() { view?.selectNext() }
    override fun selectPrevious() { view?.selectPrevious() }
    override fun unselect() { view?.unselect() }

    override fun setOnSelected(listener: (item: ViewBinder?) -> Unit) {
        this.listener = listener
        view?.setOnSelected(listener)
    }

    override fun scrollToSelected() {
        view?.scrollToSelected()
    }
}

class StaticItemsListVB(
        private val items: List<SlotVB>
) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null
    private val slotMutex = SlotMutex()

    init {
        items.forEach { it.onTap = slotMutex.openOneAtATime }
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        view.set(items)
    }

    override fun detach(view: View) {
        slotMutex.detach()
    }
}
