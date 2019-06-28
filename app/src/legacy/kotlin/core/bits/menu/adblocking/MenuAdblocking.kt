package core.bits.menu.adblocking

import core.AndroidKontext
import core.LabelVB
import core.bits.menu.MenuItemVB
import core.bits.menu.MenuItemsVB
import core.res
import gs.presentation.NamedViewBinder
import org.blokada.R

private fun createMenuAdblocking(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemsVB(ktx,
            items = listOf(
                LabelVB(ktx, label = "Turn the ad blocking on or off.".res()),
                AdblockingSwitchVB(ktx),
                LabelVB(ktx, label = "Select lists to use for blocking. Defaults are fine for most people.".res()),
                createHostsListMenuItem(ktx),
                LabelVB(ktx, label = "Add your own rules to fine tune blocking.".res()),
                createWhitelistMenuItem(ktx),
                createBlacklistMenuItem(ktx),
                LabelVB(ktx, label = "See what have been blocked or allowed recently.".res()),
                createHostsLogMenuItem(ktx)
            ),
            name = R.string.panel_section_ads.res()
    )
}

fun createAdblockingMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = R.string.panel_section_ads.res(),
            icon = R.drawable.ic_blocked.res(),
            opens = createMenuAdblocking(ktx)
    )
}
