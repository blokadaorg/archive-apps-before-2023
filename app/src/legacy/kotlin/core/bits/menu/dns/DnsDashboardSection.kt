package core.bits.menu.dns

import android.content.Context
import com.github.salomonbrys.kodein.instance
import core.*
import core.bits.AddDnsVB
import core.bits.DnsChoiceVB
import core.bits.menu.adblocking.SlotMutex
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import gs.property.IWhen
import org.blokada.R

class DnsDashboardSection(
        val ctx: Context,
        override val name: Resource = R.string.panel_section_advanced_dns.res()
) : ListViewBinder(), NamedViewBinder {

    private val ktx = ctx.ktx("DnsDashboard")
    private val filters by lazy { ktx.di().instance<Filters>() }
    private val dns by lazy { ktx.di().instance<Dns>() }

    private val slotMutex = SlotMutex()

    private var get: IWhen? = null

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        filters.apps.refresh()
        get = dns.choices.doOnUiWhenSet().then {
            dns.choices().map {
                DnsChoiceVB(it, ktx, onTap = slotMutex.openOneAtATime)
            }.apply { view.set(this); view.add(AddDnsVB(ktx), 0) }
        }
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        dns.choices.cancel(get)
    }

}
