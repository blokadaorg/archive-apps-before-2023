package core

import android.app.Activity
import android.graphics.Point
import android.view.View
import com.github.michaelbull.result.onSuccess
import com.github.salomonbrys.kodein.instance
import gs.environment.ComponentProvider
import gs.presentation.LayoutViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Request
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
        max(5, limit)
    }

    private val displayingEntries = mutableListOf<String>()
    private val openedView = SlotMutex()

    private val items = mutableListOf<SlotVB>()

    private val request = { it: Request ->
        if (!displayingEntries.contains(it.domain)) {
            displayingEntries.add(it.domain)
            val dash = if (it.blocked)
                DomainBlockedVB(it.domain, it.time, ktx, slotMutex = openedView) else
                DomainForwarderVB(it.domain, it.time, ktx, slotMutex = openedView)
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
        tunnel.Persistence.request.load(0).onSuccess {
            it.forEach(request)
        }
        ktx.on(Events.REQUEST, request)
    }

    override fun detach(view: View) {
        openedView.view = null
        displayingEntries.clear()
        ktx.cancel(Events.REQUEST, request)
    }

}
