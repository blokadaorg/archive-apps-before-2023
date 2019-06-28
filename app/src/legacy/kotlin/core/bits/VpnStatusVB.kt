package core.bits

import com.github.salomonbrys.kodein.instance
import core.*
import core.bits.menu.MENU_CLICK_BY_NAME
import gs.property.I18n
import gs.property.IWhen
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig
import java.util.*

class VpnStatusVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val s: Tunnel = ktx.di().instance(),
        private val tunnelStatus: EnabledStateActor = ktx.di().instance()
) : ByteVB() {

    override fun attach(view: ByteView) {
        ktx.on(BLOCKA_CONFIG, configListener)
        stateListener = s.enabled.doOnUiWhenChanged().then {
            update()
        }
        tunnelStatus.listeners.add(tunnelListener)
        update()
    }

    override fun detach(view: ByteView) {
        ktx.cancel(BLOCKA_CONFIG, configListener)
        tunnelStatus.listeners.remove(tunnelListener)
        s.enabled.cancel(stateListener)
    }

    private var wasActive = false
    private var active = false
    private var activating = false
    private var config: BlockaConfig = BlockaConfig()
    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        activateVpnAutomatically(cfg)
        wasActive = cfg.activeUntil.after(Date())
        Unit
    }

    private var stateListener: IWhen? = null

    private val update = { ->
        view?.run {
            when {
                !s.enabled() -> {
                    icon(R.drawable.ic_shield_key_outline.res())
                    arrow(null)
                    label("No VPN".res())
                    state("Blokada is disabled".res())
                    onTap {}
                    onArrowTap {}
                }
                config.blockaVpn && activating -> {
                    icon(R.drawable.ic_shield_key_outline.res())
                    arrow(null)
                    label("Connecting to VPN".res())
                    state("please wait...".res())
                    onTap {}
                    onArrowTap {}
                }
                !config.blockaVpn && config.activeUntil.after(Date()) -> {
                    icon(R.drawable.ic_shield_key_outline.res())
                    arrow(null)
                    label("Touch to setup VPN".res())
                    state("your account is still active".res())
                    onTap {
                        ktx.emit(MENU_CLICK_BY_NAME, "VPN".res())
                    }
                    onArrowTap {}
                }
                !config.blockaVpn -> {
                    icon(R.drawable.ic_shield_key_outline.res())
                    arrow(null)
                    label("No VPN".res())
                    state("VPN is disabled".res())
                    onTap {}
                    onArrowTap {
                        ktx.emit(MENU_CLICK_BY_NAME, "VPN".res())
                    }
                }
                else -> {
                    icon(R.drawable.ic_shield_key_outline.res())
                    arrow("Change".res())
                    label(config.gatewayNiceName.res())
                    state("connected to VPN".res())
                    onTap {}
                    onArrowTap {
                        ktx.emit(MENU_CLICK_BY_NAME, "VPN".res())
                    }
                }
            }
        }
        Unit
    }

    private val tunnelListener = object : IEnabledStateActorListener {
        override fun startActivating() {
            activating = true
            active = false
            update()
        }

        override fun finishActivating() {
            activating = false
            active = true
            update()
        }

        override fun startDeactivating() {
            activating = true
            active = false
            update()
        }

        override fun finishDeactivating() {
            activating = false
            active = false
            update()
        }
    }

    private fun activateVpnAutomatically(cfg: BlockaConfig) {
        if (!cfg.blockaVpn && !wasActive && cfg.activeUntil.after(Date()) && cfg.hasGateway()) {
            ktx.v("automatically enabling vpn on new subscription")
            ktx.emit(BLOCKA_CONFIG, cfg.copy(blockaVpn = true))
        }
    }

}
