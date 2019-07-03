package core.bits.menu

import android.content.Intent
import androidx.core.content.FileProvider
import com.github.salomonbrys.kodein.instance
import core.*
import core.bits.UpdateVB
import core.bits.menu.adblocking.createAdblockingMenuItem
import core.bits.menu.advanced.createAdvancedMenuItem
import core.bits.menu.apps.createAppsMenuItem
import core.bits.menu.dns.createDnsMenuItem
import core.bits.menu.vpn.createVpnMenuItem
import core.bits.openInBrowser
import gs.presentation.NamedViewBinder
import org.blokada.BuildConfig
import org.blokada.R
import tunnel.blokadaUserAgent
import update.newAppDetailsIntent
import java.io.File

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
                    createDonateMenuItem(ktx),
                    createAdvancedMenuItem(ktx),
                    createLearnMoreMenuItem(ktx),
                    createAboutMenuItem(ktx)
            ),
            name = R.string.panel_section_menu.res()
    )
}

fun createLearnMoreMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = "Learn more".res(),
            icon = R.drawable.ic_help_outline.res(),
            opens = createLearnMoreMenu(ktx)
    )
}

fun createLearnMoreMenu(ktx: AndroidKontext): MenuItemsVB {
    return MenuItemsVB(ktx,
            items = listOf(
                    createBlogMenuItem(ktx),
                    createCtaMenuItem(ktx),
                    createHelpMenuItem(ktx),
                    createTelegramMenuItem(ktx)
            ),
            name = "Learn more".res()
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

fun createCtaMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val page = ktx.di().instance<Pages>().cta
    return SimpleMenuItemVB(ktx,
            label = R.string.main_cta.res(),
            icon = R.drawable.ic_feedback.res(),
            action = { openInBrowser(ktx.ctx, page()) }
    )
}

fun createDonateMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val page = ktx.di().instance<Pages>().donate
    return SimpleMenuItemVB(ktx,
            label = R.string.slot_donate_action.res(),
            icon = R.drawable.ic_heart_box.res(),
            action = { openInBrowser(ktx.ctx, page()) }
    )
}

fun createTelegramMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val page = ktx.di().instance<Pages>().chat
    return SimpleMenuItemVB(ktx,
            label = "Telegram chat".res(),
            icon = R.drawable.ic_comment_multiple_outline.res(),
            action = { openInBrowser(ktx.ctx, page()) }
    )
}

fun createBlogMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val page = ktx.di().instance<Pages>().news
    return SimpleMenuItemVB(ktx,
            label = R.string.main_blog_text.res(),
            icon = R.drawable.ic_earth.res(),
            action = { openInBrowser(ktx.ctx, page()) }
    )
}

fun createAboutMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return MenuItemVB(ktx,
            label = R.string.slot_about.res(),
            icon = R.drawable.ic_info.res(),
            opens = createAboutMenu(ktx)
    )
}

fun createAboutMenu(ktx: AndroidKontext): MenuItemsVB {
    return MenuItemsVB(ktx,
            items = listOf(
                    LabelVB(ktx, label = "%s\n\n%s".format(BuildConfig.VERSION_NAME, blokadaUserAgent(ktx.ctx)).res()),
                    UpdateVB(ktx, onTap = defaultOnTap),
                    LabelVB(ktx, label = "Sharing log allows us to discover the reason of your problem. Make sure you send it privately to one of Blokada core team members.".res()),
                    createLogMenuItem(ktx),
                    LabelVB(ktx, label = "Other".res()),
                    createAppDetailsMenuItem(ktx),
                    createChangelogMenuItem(ktx),
                    createCreditsMenuItem(ktx)
            ),
            name = R.string.slot_about.res()
    )
}

fun createCreditsMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val page = ktx.di().instance<Pages>().credits
    return SimpleMenuItemVB(ktx,
            label = R.string.main_credits.res(),
            icon = R.drawable.ic_earth.res(),
            action = { openInBrowser(ktx.ctx, page()) }
    )
}

fun createChangelogMenuItem(ktx: AndroidKontext): NamedViewBinder {
    val page = ktx.di().instance<Pages>().changelog
    return SimpleMenuItemVB(ktx,
            label = R.string.main_changelog.res(),
            icon = R.drawable.ic_code_tags.res(),
            action = { openInBrowser(ktx.ctx, page()) }
    )
}

fun createLogMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return SimpleMenuItemVB(ktx,
            label = R.string.main_log.res(),
            icon = R.drawable.ic_bug_report_black_24dp.res(),
            action = {
                //                    if (askForExternalStoragePermissionsIfNeeded(activity)) {
                val uri = File(ktx.ctx.filesDir, "/blokada.log")
                val openFileIntent = Intent(Intent.ACTION_SEND)
                openFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                openFileIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                openFileIntent.type = "plain/*"
                openFileIntent.putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(ktx.ctx, "${ktx.ctx.packageName}.files",
                        uri))
                ktx.ctx.startActivity(openFileIntent)
//                    }
            }
    )
}

fun createAppDetailsMenuItem(ktx: AndroidKontext): NamedViewBinder {
    return SimpleMenuItemVB(ktx,
            label = R.string.update_button_appinfo.res(),
            icon = R.drawable.ic_info.res(),
            action = {
                try {
                    ktx.ctx.startActivity(newAppDetailsIntent(ktx.ctx.packageName))
                } catch (e: Exception) {
                }
            }
    )
}
