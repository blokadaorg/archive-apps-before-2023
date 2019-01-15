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
            InfoSlotVB(R.string.main_intro_new, ctx.ktx("InfoSlotVB"), openedView),
            InfoSlotVB(R.string.main_intro_swipe, ctx.ktx("InfoSlotVB"), openedView),
            InfoSlotVB(R.string.main_intro_remove, ctx.ktx("InfoSlotVB"), openedView),
            AppStatusSlotVB(ctx.ktx("AppStatusSlotVB"), openedView),
            DroppedCountSlotVB(ctx.ktx("DroppedCountSlotVB"), openedView)
    )

    override fun attach(view: View) {
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        openedView.view = null
    }

}
