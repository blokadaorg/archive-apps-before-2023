package core

import android.view.View
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events

class DashboardSectionVB(val section: DashboardSection) : LayoutViewBinder(R.layout.vblistview) {

    private val ktx = "section_dash".ktx()
    private var view: VBListView? = null

    private val displayingEntries = mutableListOf<String>()

    private val requestForwarded = { it: String ->
        if (!displayingEntries.contains(it)) {
            displayingEntries.add(it)
            val dash = SlotVB(Slot(SlotType.FORWARD, it))
            view?.add(dash)
        }
        Unit
    }

    private val requestBlocked = { it: String ->
        if (!displayingEntries.contains(it)) {
            displayingEntries.add(it)
            val dash = SlotVB(Slot(SlotType.BLOCK, it))
            view?.add(dash)
        }
        Unit
    }

    override fun attach(view: View) {
        this.view = view as VBListView
        ktx.on(Events.REQUEST_BLOCKED, requestBlocked)
        if (section.nameResId == R.string.dashboard_name_explore)
            ktx.on(Events.REQUEST_FORWARDED, requestForwarded)
    }

    override fun detach(view: View) {
        ktx.cancel(Events.REQUEST_BLOCKED, requestBlocked)
        if (section.nameResId == R.string.dashboard_name_explore)
            ktx.cancel(Events.REQUEST_FORWARDED, requestForwarded)
    }

}
