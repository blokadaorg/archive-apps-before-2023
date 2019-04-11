package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.presentation.LayoutViewBinder
import gs.property.IWhen
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class AllAppsDashboardSectionVB(val ctx: Context, val system: Boolean) : LayoutViewBinder(R.layout.vblistview),
    Scrollable, ListSection {

    private val ktx = ctx.ktx("AllAppsDashboard")
    private val filters by lazy { ktx.di().instance<Filters>() }
    private val filterManager by lazy { ktx.di().instance<tunnel.Main>() }
    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        Unit
    }

    private var getApps: IWhen? = null

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        ktx.on(Events.FILTERS_CHANGED, updateApps)
        filters.apps.refresh()
        getApps = filters.apps.doOnUiWhenSet().then {
            filters.apps().filter { it.system == system }.map {
                AppVB(it, ktx, onTap = slotMutex.openOneAtATime)
            }.apply { view.set(this) }
        }
    }

    override fun detach(view: View) {
        slotMutex.detach()
        ktx.cancel(Events.FILTERS_CHANGED, updateApps)
        filters.apps.cancel(getApps)
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
