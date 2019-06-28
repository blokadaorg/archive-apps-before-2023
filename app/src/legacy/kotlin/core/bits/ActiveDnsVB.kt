package core.bits

import android.content.Context
import android.os.Handler
import com.github.salomonbrys.kodein.instance
import core.*
import core.bits.menu.MENU_CLICK_BY_NAME
import gs.property.I18n
import gs.property.IWhen
import org.blokada.R

class ActiveDnsVB(
        private val ktx: AndroidKontext,
        private val ctx: Context = ktx.ctx,
        private val i18n: I18n = ktx.di().instance(),
        private val dns: Dns = ktx.di().instance()
) : BitVB() {

    private var dnsServersChanged: IWhen? = null
    private var dnsEnabledChanged: IWhen? = null

    override fun attach(view: BitView) {
        dnsServersChanged = dns.dnsServers.doOnUiWhenSet().then(update)
        dnsEnabledChanged = dns.enabled.doOnUiWhenSet().then(update)
    }

    override fun detach(view: BitView) {
        dns.dnsServers.cancel(dnsServersChanged)
        dns.enabled.cancel(dnsEnabledChanged)
    }

    private val update = {
        view?.run {
            val item = dns.choices().first { it.active }
            val id = if (item.id.startsWith("custom")) "custom" else item.id
            val name = i18n.localisedOrNull("dns_${id}_name") ?: id.capitalize()

            if (dns.enabled() && dns.hasCustomDnsSelected()) {
                icon(R.drawable.ic_server.res(), color = R.color.switch_on.res())
                label(i18n.getString(R.string.slot_dns_name, name).res())
            } else {
                icon(R.drawable.ic_server.res())
                label(R.string.slot_dns_name_disabled.res())
            }

            if (dns.enabled() && !dns.hasCustomDnsSelected()) {
                Handler {
                    ktx.emit(MENU_CLICK_BY_NAME, R.string.panel_section_advanced_dns.res())
                    true
                }.sendEmptyMessageDelayed(0, 300)
            }

            switch(dns.enabled())
            onSwitch {
                dns.enabled %= it
            }

        }
        Unit
    }
}
