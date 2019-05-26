package core

import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.instance
import gs.presentation.ListViewBinder
import gs.presentation.ViewBinder
import gs.presentation.WebDash

class StartDashboardSectionVB(
        val ktx: AndroidKontext,
        val pages: Pages = ktx.di().instance()
) : ListViewBinder() {

    private val slotMutex = SlotMutex()

    private var items = listOf<ViewBinder>(
            WebDash(LazyKodein(ktx.di), pages.intro, reloadOnError = true, javascript = true, small = true),
            AdblockingVB(ktx, onTap = slotMutex.openOneAtATime),
            BlockaVB(ktx, onTap = slotMutex.openOneAtATime),
            AccountVB(ktx, onTap = slotMutex.openOneAtATime)
    )

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        view.set(items)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
    }
}
