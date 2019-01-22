package core

import android.content.Context
import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R

class AdvancedDashboardSectionVB(val ctx: Context) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val openedView = SlotMutex()

    private val items = mutableListOf(
            FiltersStatusVB(ctx.ktx("FiltersStatusSlotVB"), slotMutex = openedView),
            ActiveDnsVB(ctx.ktx("currentDns"), slotMutex = openedView),
            UpdateVB(ctx.ktx("updateVB"), slotMutex = openedView)
    )

    override fun attach(view: View) {
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        openedView.view = null
    }

}
