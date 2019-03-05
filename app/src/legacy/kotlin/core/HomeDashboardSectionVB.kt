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
) : LayoutViewBinder(R.layout.vblistview) {

    private var view: VBListView? = null

    private val openedView = SlotMutex()

    private val intro: IntroVB = IntroVB(ctx.ktx("InfoSlotVB"), slotMutex = openedView, onRemove = {
        items = items.subList(1, items.size)
        view?.set(items)
        introPersistence.write(true)
    })

    private val batteryVB: BatteryVB = BatteryVB(ctx.ktx("BatteryVB"), slotMutex = openedView, onRemove = {
        items = items.subList(0, items.size - 1)
        view?.set(items)
    })

    private var items = listOf<ViewBinder>(
            AppStatusVB(ctx.ktx("AppStatusSlotVB"), slotMutex = openedView),
            DroppedCountVB(ctx.ktx("DroppedCountSlotVB"), slotMutex = openedView),
            HomeNotificationsVB(ctx.ktx("NotificationsVB"), slotMutex = openedView)
    )

    override fun attach(view: View) {
        if (!introPersistence.read(false)) items = listOf(intro) + items
        if (!battery.isWhitelisted()) items += batteryVB
        this.view = view as VBListView
        view.set(items)
    }

    override fun detach(view: View) {
        openedView.view = null
    }

}
