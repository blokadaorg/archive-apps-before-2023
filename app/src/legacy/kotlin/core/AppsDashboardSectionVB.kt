package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class AppsDashboardSectionVB(val ctx: Context) : LayoutViewBinder(R.layout.vblistview) {

    private val ktx = ctx.ktx("AppsDashboard")
    private val filters by lazy { ktx.di().instance<Filters>() }
    private val filterManager by lazy { ktx.di().instance<tunnel.Main>() }
    private var view: VBListView? = null

    private val openedView = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        filters.filter { it.source.id == "app" }
                .filter { it.active }
                .map {
            HomeAppVB(it, ktx, slotMutex = openedView)
        }.apply { view?.set(this) }
        Unit
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        ktx.on(Events.FILTERS_CHANGED, updateApps)
    }

    override fun detach(view: View) {
        openedView.view = null
        ktx.cancel(Events.FILTERS_CHANGED, updateApps)
    }

}
