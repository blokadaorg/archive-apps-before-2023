package core.bits.menu

import com.github.salomonbrys.kodein.instance
import core.AndroidKontext
import core.LabelVB
import core.Pages
import core.bits.menu.adblocking.createAdblockingMenuItem
import core.bits.menu.advanced.createAdvancedMenuItem
import core.bits.menu.apps.createAppsMenuItem
import core.bits.menu.dns.createDnsMenuItem
import core.bits.menu.vpn.createVpnMenuItem
import core.bits.openInBrowser
import core.res
import gs.presentation.NamedViewBinder
import org.blokada.R

fun createMenu(ktx: AndroidKontext): MenuItemsVB {
    return MenuItemsVB(ktx,
            items = listOf(
                    LabelVB(ktx, label = "Configure Blokada features".res()),
                    createAdblockingMenuItem(ktx),
                    createVpnMenuItem(ktx),
                    createDnsMenuItem(ktx),
                    LabelVB(ktx, label = "Whitelist apps that do not work correctly".res()),
                    createAppsMenuItem(ktx),
                    LabelVB(ktx, label = "Dive in deeper".res()),
                    createAdvancedMenuItem(ktx),
                    createHelpMenuItem(ktx)
            ),
            name = R.string.panel_section_menu.res()
    )
}

fun createHelpMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val helpPage = ktx.di().instance<Pages>().help
    return SimpleMenuItemVB(ktx,
            label = R.string.panel_section_home_help.res(),
            icon = R.drawable.ic_help_outline.res(),
            action = { openInBrowser(ktx.ctx, helpPage()) }
    )
}
