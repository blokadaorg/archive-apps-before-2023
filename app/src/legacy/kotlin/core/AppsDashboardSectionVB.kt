package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class AppsDashboardSectionVB(val ctx: Context) : LayoutViewBinder(R.layout.vblistview),
    Scrollable, ListSection {

    private val ktx = ctx.ktx("AppsDashboard")
    private val filters by lazy { ktx.di().instance<Filters>() }
    private val filterManager by lazy { ktx.di().instance<tunnel.Main>() }
    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        filters.filter { it.source.id == "app" }
                .filter { it.active }
                .map {
            HomeAppVB(it, ktx, onTap = slotMutex.openOneAtATime)
        }.apply { view?.set(this) }
        Unit
    }

    override fun attach(view: View) {
        this.view = view as VBListView
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

    private var listener: (item: SlotVB?) -> Unit = {}

    override fun setOnSelected(listener: (item: SlotVB?) -> Unit) {
        this.listener = listener
        view?.setOnSelected(listener)
    }

    override fun scrollToSelected() {
        view?.scrollToSelected()
    }
}
