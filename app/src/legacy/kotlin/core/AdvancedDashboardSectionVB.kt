package core

import android.content.Context
import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R

class AdvancedDashboardSectionVB(val ctx: Context) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val openedView = SlotMutex()

    private val items = mutableListOf<SlotVB>(
            FiltersStatusSlotVB(ctx.ktx("FiltersStatusSlotVB"), openedView),
            MemoryLimitSlotVB(ctx.ktx("MemoryLimitSlotVB"), openedView),
            DnsCurrentVB(ctx.ktx("currentDns"), slotMutex = openedView)
    )

    override fun attach(view: View) {
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        openedView.view = null
    }

}
