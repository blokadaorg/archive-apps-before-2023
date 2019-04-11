package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.presentation.LayoutViewBinder
import gs.property.IWhen
import org.blokada.R

class DnsDashboardSection(val ctx: Context) : LayoutViewBinder(R.layout.vblistview),
    ListSection, Scrollable {

    private val ktx = ctx.ktx("DnsDashboard")
    private val filters by lazy { ktx.di().instance<Filters>() }
    private val dns by lazy { ktx.di().instance<Dns>() }
    private var view: VBListView? = null

    private val slotMutex = SlotMutex()

    private var get: IWhen? = null

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        filters.apps.refresh()
        get = dns.choices.doOnUiWhenSet().then {
            dns.choices().map {
                DnsChoiceVB(it, ktx, onTap = slotMutex.openOneAtATime)
            }.apply { view.set(this) }
        }
    }

    override fun detach(view: View) {
        slotMutex.detach()
        dns.choices.cancel(get)
    }

    override fun setOnScroll(onScrollDown: () -> Unit, onScrollUp: () -> Unit, onScrollStopped: () -> Unit) = Unit

    override fun getScrollableView() = view!!

    override fun selectNext() { view?.selectNext() }
    override fun selectPrevious() { view?.selectPrevious() }
    override fun unselect() { view?.unselect() }

    private var listener: (SlotVB?) -> Unit = {}
    override fun setOnSelected(listener: (item: SlotVB?) -> Unit) {
        this.listener = listener
        view?.setOnSelected(listener)
    }

    override fun scrollToSelected() {
        view?.scrollToSelected()
    }
}
