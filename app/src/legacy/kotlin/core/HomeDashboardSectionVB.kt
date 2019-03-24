package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.instance
import gs.presentation.LayoutViewBinder
import gs.presentation.ViewBinder
import gs.property.BasicPersistence
import org.blokada.R

class HomeDashboardSectionVB(
        val ktx: AndroidKontext,
        val ctx: Context = ktx.ctx,
        val battery: Battery = ktx.di().instance(),
        val introPersistence: BasicPersistence<Boolean> = BasicPersistence(LazyKodein(ktx.di), "intro_vb")
) : LayoutViewBinder(R.layout.vblistview), Scrollable, ListSection {

    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private val intro: IntroVB = IntroVB(ctx.ktx("InfoSlotVB"), onTap = slotMutex.openOneAtATime, onRemove = {
        items = items.subList(1, items.size)
        view?.set(items)
        introPersistence.write(true)
    })

    private val batteryVB: BatteryVB = BatteryVB(ctx.ktx("BatteryVB"), onTap = slotMutex.openOneAtATime, onRemove = {
        items = items.subList(0, items.size - 1)
        view?.set(items)
    })

    private var items = listOf<ViewBinder>(
            AppStatusVB(ctx.ktx("AppStatusSlotVB"), onTap = slotMutex.openOneAtATime),
            DroppedCountVB(ctx.ktx("DroppedCountSlotVB"), onTap = slotMutex.openOneAtATime),
            HomeNotificationsVB(ctx.ktx("NotificationsVB"), onTap = slotMutex.openOneAtATime)
    )

    override fun attach(view: View) {
        if (!introPersistence.read(false)) items = listOf(intro) + items
        if (!battery.isWhitelisted()) items += batteryVB
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        slotMutex.detach()
    }

    override fun setOnScroll(onScrollDown: () -> Unit, onScrollUp: () -> Unit, onScrollStopped: () -> Unit) = Unit

    override fun getScrollableView() = view!!

    override fun selectNext() { view?.selectNext() }
    override fun selectPrevious() { view?.selectPrevious() }
    override fun unselect() { view?.unselect() }

    override fun setOnSelected(listener: (item: ViewBinder?) -> Unit) {
        view?.setOnSelected(listener)
    }

    override fun scrollToSelected() {
        view?.scrollToSelected()
    }
}
