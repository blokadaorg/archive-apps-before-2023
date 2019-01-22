package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.environment.inject
import gs.presentation.LayoutViewBinder
import org.blokada.R

class HomeDashboardSectionVB(val ctx: Context) : LayoutViewBinder(R.layout.vblistview) {

    private val ktx = "section_home".ktx()
    private var view: VBListView? = null

    private val openedView = SlotMutex()
    private val tunnelEvents by lazy { ctx.inject().instance<EnabledStateActor>() }
    private val tunnelEvents2 by lazy { ctx.inject().instance<Tunnel>() }

    private val items = mutableListOf(
            IntroVB(ctx.ktx("InfoSlotVB"), slotMutex = openedView, onRemove = {}),
            AppStatusVB(ctx.ktx("AppStatusSlotVB"), slotMutex = openedView),
            DroppedCountVB(ctx.ktx("DroppedCountSlotVB"), slotMutex = openedView),
            HomeNotificationsVB(ctx.ktx("NotificationsVB"), slotMutex = openedView)
    )

    override fun attach(view: View) {
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        openedView.view = null
    }

}
