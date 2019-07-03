package core.bits.menu.vpn

import com.github.salomonbrys.kodein.instance
import core.AndroidKontext
import core.LabelVB
import core.Pages
import core.bits.menu.MenuItemVB
import core.bits.menu.MenuItemsVB
import core.bits.menu.SimpleMenuItemVB
import core.bits.openInBrowser
import core.res
import gs.presentation.NamedViewBinder
import org.blokada.R

private fun createMenuVpn(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemsVB(ktx,
            items = listOf(
                LabelVB(ktx, label = "With VPN your data in encrypted, your IP address is hidden, and your location is changed. Learn more about the benefits here:".res()),
                createWhyVpnMenuItem(ktx),
                VpnSwitchVB(ktx),
                LabelVB(ktx, label = "In order to use the VPN, you need active account. Check or restore your account here.".res()),
                createManageAccountMenuItem(ktx),
                LabelVB(ktx, label = "Select one of the gateways to connect to. Your device will send your encrypted data securely through that gateway, and you will stay anonymous.".res()),
                createGatewaysMenuItem(ktx)
            ),
            name = "VPN".res()
    )
}

fun createVpnMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = "VPN".res(),
            icon = R.drawable.ic_shield_key_outline.res(),
            opens = createMenuVpn(ktx)
    )
}

fun createManageAccountMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = "Account".res(),
            icon = R.drawable.ic_account_circle_black_24dp.res(),
            opens = createAccountMenu(ktx)
    )
}

fun createGatewaysMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = "Gateways".res(),
            icon = R.drawable.ic_server.res(),
            opens = GatewaysDashboardSectionVB(ktx)
    )
}

fun createWhyVpnMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val whyPage = ktx.di().instance<Pages>().vpn
    return SimpleMenuItemVB(ktx,
            label = "Why VPN?".res(),
            icon = R.drawable.ic_help_outline.res(),
            action = { openInBrowser(ktx.ctx, whyPage()) }
    )
}

private fun createAccountMenu(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemsVB(ktx,
            items = listOf(
                    LabelVB(ktx, label = "Manage your account subscription here:".res()),
                    AccountVB(ktx),
                    LabelVB(ktx, label = "Your account ID is secret and you should keep it to yourself. Write it down in case you uninstall the app.".res()),
                    CopyAccountVB(ktx),
                    LabelVB(ktx, label = "Here you can restore your existing account if, for example, you re-installed the app.".res()),
                    RestoreAccountVB(ktx)
            ),
            name = "Account".res()
    )
}
