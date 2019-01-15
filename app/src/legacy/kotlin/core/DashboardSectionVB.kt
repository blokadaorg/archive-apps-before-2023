package core

import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import java.util.*

class DashboardSectionVB(val ktx: AndroidKontext, val section: DashboardSection) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val displayingEntries = mutableListOf<String>()
    private val openedView = SlotMutex()

    private val items = mutableListOf<SlotVB>()

    private val requestForwarded = { it: String ->
        if (!displayingEntries.contains(it)) {
            displayingEntries.add(it)
            val dash = ForwardSlotVB(it, Date(), ktx, openedView)
            items.add(dash)
            view?.add(dash)
            trimListIfNecessary()
        }
        Unit
    }

    private val requestBlocked = { it: String ->
        if (!displayingEntries.contains(it)) {
            displayingEntries.add(it)
            val dash = BlockSlotVB(it, Date(), ktx, openedView)
            items.add(dash)
            view?.add(dash)
            trimListIfNecessary()
        }
        Unit
    }

    private fun trimListIfNecessary() {
        if (items.size > 10) {
            items.firstOrNull()?.apply {
                items.remove(this)
                view?.remove(this)
            }
        }
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        ktx.on(Events.REQUEST_BLOCKED, requestBlocked)
        if (section.nameResId == R.string.dashboard_name_ads)
            ktx.on(Events.REQUEST_FORWARDED, requestForwarded)
    }

    override fun detach(view: View) {
        openedView.view = null
        displayingEntries.clear()
        ktx.cancel(Events.REQUEST_BLOCKED, requestBlocked)
        if (section.nameResId == R.string.dashboard_name_ads)
            ktx.cancel(Events.REQUEST_FORWARDED, requestForwarded)
    }

}
