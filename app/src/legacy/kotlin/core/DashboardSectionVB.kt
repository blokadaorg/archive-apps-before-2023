package core

import android.app.Activity
import android.graphics.Point
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.environment.ComponentProvider
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import java.util.*
import kotlin.math.max

class DashboardSectionVB(
        val ktx: AndroidKontext,
        val section: DashboardSection,
        val activity: ComponentProvider<Activity> = ktx.di().instance()
) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val screenHeight: Int by lazy {
        val point = Point()
        activity.get()?.windowManager?.defaultDisplay?.getSize(point)
        if (point.y > 0) point.y else 2000
    }

    private val countLimit: Int by lazy {
        val limit = screenHeight / ktx.ctx.dpToPx(80)
        ktx.e("limit: ${limit}, screenHeight: ${screenHeight}")
        max(5, limit)
    }

    private val displayingEntries = mutableListOf<String>()
    private val openedView = SlotMutex()

    private val items = mutableListOf<SlotVB>()

    private val requestForwarded = { it: String ->
        if (!displayingEntries.contains(it)) {
            displayingEntries.add(it)
            val dash = DomainForwarderVB(it, Date(), ktx, slotMutex = openedView)
            items.add(dash)
            view?.add(dash)
            trimListIfNecessary()
        }
        Unit
    }

    private val requestBlocked = { it: String ->
        if (!displayingEntries.contains(it)) {
            displayingEntries.add(it)
            val dash = DomainBlockedVB(it, Date(), ktx, slotMutex = openedView)
            items.add(dash)
            view?.add(dash)
            trimListIfNecessary()
        }
        Unit
    }

    private fun trimListIfNecessary() {
        if (items.size > countLimit) {
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
