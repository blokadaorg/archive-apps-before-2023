package core

import android.content.Context
import android.view.View
import com.github.salomonbrys.kodein.instance
import gs.presentation.LayoutViewBinder
import gs.property.IWhen
import org.blokada.R

class DnsDashboardSection(val ctx: Context) : LayoutViewBinder(R.layout.vblistview) {

    private val ktx = ctx.ktx("DnsDashboard")
    private val filters by lazy { ktx.di().instance<Filters>() }
    private val dns by lazy { ktx.di().instance<Dns>() }
    private var view: VBListView? = null

    private val mutex = SlotMutex()

    private var get: IWhen? = null

    override fun attach(view: View) {
        this.view = view as VBListView
        view.enableAlternativeMode()
        filters.apps.refresh()
        get = dns.choices.doOnUiWhenSet().then {
            dns.choices().map {
                DnsChoiceVB(it, ktx, slotMutex = mutex)
            }.apply { view.set(this) }
        }
    }

    override fun detach(view: View) {
        mutex.view = null
        dns.choices.cancel(get)
    }

}
