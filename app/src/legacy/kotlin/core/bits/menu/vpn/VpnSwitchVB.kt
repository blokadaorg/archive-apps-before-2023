package core.bits.menu.vpn

import core.AndroidKontext
import core.BitVB
import core.BitView
import core.res
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig

class VpnSwitchVB(
        private val ktx: AndroidKontext
) : BitVB() {

    override fun attach(view: BitView) {
        view.label("Use VPN".res())
        view.icon(R.drawable.ic_shield_key_outline.res())
        view.alternative(true)
        ktx.on(BLOCKA_CONFIG, update)
    }

    override fun detach(view: BitView) {
        ktx.cancel(BLOCKA_CONFIG, update)
    }

    private val update = { cfg: BlockaConfig ->
        view?.run {
            switch(cfg.blockaVpn)
            onSwitch {
                ktx.emit(BLOCKA_CONFIG, cfg.copy(blockaVpn = !cfg.blockaVpn))
            }
        }
        Unit
    }

}
