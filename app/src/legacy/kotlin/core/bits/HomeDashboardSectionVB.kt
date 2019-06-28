package core.bits

import android.content.Context
import android.content.Intent
import com.github.michaelbull.result.get
import com.github.salomonbrys.kodein.instance
import core.*
import core.bits.menu.MENU_CLICK_BY_NAME
import core.bits.menu.isLandscape
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import gs.presentation.ViewBinder
import gs.property.IWhen
import gs.property.Version
import org.blokada.BuildConfig
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig
import tunnel.showSnack
import java.util.*

data class SlotsSeenStatus(
        val intro: Boolean = false,
        val telegram: Boolean = false,
        val blog: Boolean = false,
        val updated: Int = BuildConfig.VERSION_CODE,
        val cta: Int = 0,
        val donate: Int = 0
)

class SlotStatusPersistence {
    val load = { ->
        Result.of { Persistence.paper().read<SlotsSeenStatus>("slots:status", SlotsSeenStatus()) }
    }
    val save = { slots: SlotsSeenStatus ->
        Result.of { Persistence.paper().write("slots:status", slots) }
    }
}

class Home2DashboardSectionVB(
        val ktx: AndroidKontext,
        val ctx: Context = ktx.ctx,
        val version: Version = ktx.di().instance(),
        val welcome: Welcome = ktx.di().instance(),
        override val name: Resource = R.string.panel_section_home.res()
) : ListViewBinder(), NamedViewBinder {

    override fun attach(view: VBListView) {
        val (slot, name) = decideOnSlot()
        if (slot != null && added == null) {
            items = listOf(slot) + items
            added = name
            if (slot is SimpleByteVB) slot.onTapped = {
                // Remove this slot
                markAsSeen()
                items = items.subList(1, items.size)
                view.set(items)
            }
        }
        view.set(items)
        if (isLandscape(ktx.ctx)) {
            view.enableLandscapeMode(reversed = true)
            view.set(items.reversed())
        }
    }

    private var items = listOf<ViewBinder>(
            AdsBlockedVB(ktx),
            VpnStatusVB(ktx),
            Adblocking2VB(ktx),
            VpnVB(ktx),
            ActiveDnsVB(ktx),
            HomeNotificationsVB(ktx)
    )

    private var added: OneTimeByte? = null
    private val oneTimeBytes = createOneTimeBytes(ktx)

    private fun markAsSeen() {
        val cfg = Persistence.slots.load().get()!!
        val newCfg = when(added) {
            OneTimeByte.UPDATED -> cfg.copy(updated = BuildConfig.VERSION_CODE)
            OneTimeByte.DONATE -> cfg.copy(donate = BuildConfig.VERSION_CODE)
            else -> cfg
        }
        Persistence.slots.save(newCfg)
    }

    private fun decideOnSlot(): Pair<ViewBinder?, OneTimeByte?> {
        val cfg = Persistence.slots.load().get()!!
        val name = when {
            isLandscape(ktx.ctx) -> null
            BuildConfig.VERSION_CODE > cfg.updated -> OneTimeByte.UPDATED
            BuildConfig.VERSION_CODE > cfg.donate -> OneTimeByte.DONATE
            version.obsolete() -> OneTimeByte.OBSOLETE
            getInstalledBuilds().size > 1 -> OneTimeByte.CLEANUP
            else -> null
        }
        return oneTimeBytes[name] to name
    }

    private fun getInstalledBuilds(): List<String> {
        return welcome.conflictingBuilds().map {
            if (isPackageInstalled(it)) it else null
        }.filterNotNull()
    }

    private fun isPackageInstalled(appId: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(appId) as Intent? ?: return false
        val activities = ctx.packageManager.queryIntentActivities(intent, 0)
        return activities.size > 0
    }
}

class HomeNotificationsVB(
        private val ktx: AndroidKontext,
        private val ui: UiState = ktx.di().instance()
) : BitVB() {

    private var listener: IWhen? = null

    override fun attach(view: BitView) {
        listener = ui.notifications.doOnUiWhenSet().then {
            if (ui.notifications()) {
                view.label(R.string.slot_notifications_enabled.res())
                view.icon(R.drawable.ic_info.res(), color = R.color.switch_on.res())
            } else {
                view.label(R.string.slot_notifications_disabled.res())
                view.icon(R.drawable.ic_info.res())
            }

            view.switch(ui.notifications())
            view.onSwitch { ui.notifications %= it }
        }
    }

    override fun detach(view: BitView) {
        ui.notifications.cancel(listener)
    }
}

class VpnVB(
        private val ktx: AndroidKontext,
        private val s: Tunnel = ktx.di().instance()
): BitVB() {

    override fun attach(view: BitView) {
        ktx.on(BLOCKA_CONFIG, configListener)
        update()
    }

    override fun detach(view: BitView) {
        ktx.cancel(BLOCKA_CONFIG, configListener)
    }

    private var config: BlockaConfig = BlockaConfig()
    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        Unit
    }

    private val update = {
        view?.apply {
            if (config.blockaVpn) {
                label(R.string.home_vpn_enabled.res())
                icon(R.drawable.ic_verified.res(), color = R.color.switch_on.res())
            } else {
                label(R.string.home_vpn_disabled.res())
                icon(R.drawable.ic_shield_outline.res())
            }
            switch(config.blockaVpn)
            onSwitch { turnOn ->
                when {
                    turnOn && config.activeUntil.before(Date()) -> {
                        ktx.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                        showSnack(R.string.menu_vpn_activate_account.res())
                        switch(false)
                    }
                    turnOn && !config.hasGateway() -> {
                        ktx.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                        showSnack(R.string.menu_vpn_select_gateway.res())
                        switch(false)
                    }
                    !turnOn && !config.adblocking -> {
                        s.enabled %= false
                        ktx.emit(BLOCKA_CONFIG, config.copy(blockaVpn = turnOn))
                    }
                    else -> {
                        ktx.emit(BLOCKA_CONFIG, config.copy(blockaVpn = turnOn))
                    }
                }
            }
        }
        Unit
    }
}

class Adblocking2VB(
        private val ktx: AndroidKontext,
        private val s: Tunnel = ktx.di().instance()
): BitVB() {

    override fun attach(view: BitView) {
        ktx.on(BLOCKA_CONFIG, configListener)
        update()
    }

    override fun detach(view: BitView) {
        ktx.cancel(BLOCKA_CONFIG, configListener)
    }

    private var config: BlockaConfig = BlockaConfig()
    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        Unit
    }

    private val update = {
        view?.apply {
            if (config.adblocking) {
                label(R.string.home_adblocking_enabled.res())
                icon(R.drawable.ic_blocked.res(), color = R.color.switch_on.res())
            } else {
                label(R.string.home_adblocking_disabled.res())
                icon(R.drawable.ic_show.res())
            }
            switch(config.adblocking)
            onSwitch { adblocking ->
                if (!adblocking && !config.blockaVpn) s.enabled %= false
                ktx.emit(BLOCKA_CONFIG, config.copy(adblocking = adblocking))
            }
        }
        Unit
    }

}

class SimpleByteVB(
    private val ktx: AndroidKontext,
    private val label: Resource,
    private val description: Resource,
    private val onTap: (ktx: AndroidKontext) -> Unit,
    var onTapped: () -> Unit = {}
): ByteVB() {
    override fun attach(view: ByteView) {
        view.icon(null)
        view.label(label)
        view.state(description, smallcap = false)
        view.onTap {
            onTap(ktx)
            onTapped()
        }
    }
}

enum class OneTimeByte {
    CLEANUP, UPDATED, OBSOLETE, DONATE
}

fun createOneTimeBytes(ktx: AndroidKontext) = mapOf(
        OneTimeByte.CLEANUP to CleanupVB(ktx),
        OneTimeByte.UPDATED to SimpleByteVB(ktx,
                label = R.string.home_whats_new.res(),
                description = R.string.slot_updated_desc.res(),
                onTap = { ktx ->
                    val pages: Pages = ktx.di().instance()
                    openInBrowser(ktx.ctx, pages.updated())
                }
        ),
        OneTimeByte.OBSOLETE to SimpleByteVB(ktx,
                label = R.string.home_update_required.res(),
                description = R.string.slot_obsolete_desc.res(),
                onTap = { ktx ->
                    val pages: Pages = ktx.di().instance()
                    openInBrowser(ktx.ctx, pages.download())
                }
        ),
        OneTimeByte.DONATE to SimpleByteVB(ktx,
                label = R.string.home_donate.res(),
                description = R.string.slot_donate_desc.res(),
                onTap = { ktx ->
                    val pages: Pages = ktx.di().instance()
                    openInBrowser(ktx.ctx, pages.donate())
                }
        )
)
