package core.bits

import android.content.Context
import android.content.Intent
import com.github.michaelbull.result.get
import com.github.salomonbrys.kodein.instance
import core.*
import core.bits.menu.adblocking.SlotMutex
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

data class SlotsSeenStatus(
        val intro: Boolean = false,
        val telegram: Boolean = false,
        val blog: Boolean = false,
        val updated: Int = BuildConfig.VERSION_CODE,
        val cta: Int = 0,
        val donate: Int = 0
)

class HomeDashboardSectionVB(
        val ktx: AndroidKontext,
        val ctx: Context = ktx.ctx,
        val version: Version = ktx.di().instance(),
        val welcome: Welcome = ktx.di().instance()
) : ListViewBinder() {

    private val slotMutex = SlotMutex()

    val markAsSeen = {
        val removed = items[0]
        markAsSeen(removed)
        items = items.subList(1, items.size)
        view?.set(items)
        slotMutex.detach()
    }

    val intro: IntroVB = IntroVB(ctx.ktx("InfoSlotVB"), onTap = slotMutex.openOneAtATime,
            onRemove = markAsSeen)
    val updated = UpdatedVB(ctx.ktx("UpdatedVB"), onTap = slotMutex.openOneAtATime, onRemove = markAsSeen)
    val obsolete = ObsoleteVB(ctx.ktx("ObsoleteVB"), onTap = slotMutex.openOneAtATime)
    val cleanup = CleanupVB(ctx.ktx("CleanupVB"), onTap = slotMutex.openOneAtATime)
    val donate = DonateVB(ctx.ktx("DonateVB"), onTap = slotMutex.openOneAtATime, onRemove = markAsSeen)
    val cta = CtaVB(ctx.ktx("CtaVB"), onTap = slotMutex.openOneAtATime, onRemove = markAsSeen)
    val blog = BlogVB(ctx.ktx("BlogVB"), onTap = slotMutex.openOneAtATime, onRemove = markAsSeen)
    val telegram = TelegramVB(ctx.ktx("TelegramVB"), onTap = slotMutex.openOneAtATime, onRemove = markAsSeen)

    private var items = listOf<ViewBinder>(
            AppStatusVB(ctx.ktx("AppStatusSlotVB"), onTap = slotMutex.openOneAtATime),
            ProtectionVB(ctx.ktx("ProtectionVB"), onTap = slotMutex.openOneAtATime),
            HelpVB(ctx.ktx("HelpVB"), onTap = slotMutex.openOneAtATime)
    )

    private var added = false

    override fun attach(view: VBListView) {
        val slot = decideOnSlot()
        if (slot != null && !added) {
            items = listOf(slot) + items
            added = true
        }
        view.set(items)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
    }

    private fun markAsSeen(slot: ViewBinder) {
        val cfg = Persistence.slots.load().get()!!
        val newCfg = when {
            slot == cta -> cfg.copy(cta = cfg.cta + 1)
            slot == donate -> cfg.copy(donate = cfg.donate + 1)
            slot == blog -> cfg.copy(blog = true)
            slot == updated -> cfg.copy(updated = BuildConfig.VERSION_CODE)
            slot == telegram -> cfg.copy(telegram = true)
            slot == intro -> cfg.copy(intro = true)
            else -> cfg
        }
        Persistence.slots.save(newCfg)
    }

    private fun decideOnSlot(): ViewBinder? {
        val cfg = Persistence.slots.load().get()!!
        return when {
            !cfg.intro -> intro
            BuildConfig.VERSION_CODE > cfg.updated -> updated
            version.obsolete() -> obsolete
            getInstalledBuilds().size > 1 -> cleanup
            !cfg.telegram -> telegram
            !cfg.blog -> blog
            cfg.cta < cfg.donate -> cta
            else -> donate
        }
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


    private var items = listOf<ViewBinder>(
            AdsBlockedVB(ktx),
            VpnStatusVB(ktx),
            Adblocking2VB(ktx),
            VpnVB(ktx),
            ActiveDnsVB(ktx),
            HomeNotificationsVB(ktx)
    )

    override fun attach(view: VBListView) {
        if (isLandscape(ktx.ctx)) view.enableLandscapeMode()
        view.set(items)
    }

    override fun detach(view: VBListView) {
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
        private val ktx: AndroidKontext
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
                label("VPN is enabled".res())
                icon(R.drawable.ic_verified.res(), color = R.color.switch_on.res())
            } else {
                label("VPN is disabled".res())
                icon(R.drawable.ic_shield_outline.res())
            }
            switch(config.blockaVpn)
            onSwitch {
                ktx.emit(BLOCKA_CONFIG, config.copy(blockaVpn = it))
            }
        }
        Unit
    }
}

class Adblocking2VB(
        private val ktx: AndroidKontext
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
                label("Ad blocking is enabled".res())
                icon(R.drawable.ic_blocked.res(), color = R.color.switch_on.res())
            } else {
                label("Ad blocking is disabled".res())
                icon(R.drawable.ic_show.res())
            }
            switch(config.adblocking)
            onSwitch {
                ktx.emit(BLOCKA_CONFIG, config.copy(adblocking = it))
            }
        }
        Unit
    }

}

