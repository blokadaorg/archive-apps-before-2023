package core

import android.content.Context
import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R

class AdvancedDashboardSectionVB(val ctx: Context) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private val items = mutableListOf(
            FiltersStatusVB(ctx.ktx("FiltersStatusSlotVB"), onTap = slotMutex.openOneAtATime),
            ActiveDnsVB(ctx.ktx("currentDns"), onTap = slotMutex.openOneAtATime),
            UpdateVB(ctx.ktx("updateVB"), onTap = slotMutex.openOneAtATime),
            TelegramVB(ctx.ktx("telegramVB"), onTap = slotMutex.openOneAtATime)
    )

    override fun attach(view: View) {
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        slotMutex.detach()
    }

}
