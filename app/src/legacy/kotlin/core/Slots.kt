package core

import android.content.Intent
import android.view.View
import com.github.salomonbrys.kodein.instance
import filter.hostnameRegex
import filter.sourceToIcon
import filter.sourceToName
import gs.property.I18n
import gs.property.IWhen
import org.blokada.R
import tunnel.Events
import tunnel.Filter
import tunnel.FilterSourceDescriptor
import java.net.URL
import java.util.*

class AppStatusSlotVB(
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx
    private val tunnelEvents by lazy { ktx.di().instance<EnabledStateActor>() }
    private var view: SlotView? = null

    override fun attach(view: View) {
        super.attach(view)
        this.view = view as SlotView
        view.type = Slot.Type.STATUS
        tunnelEvents.listeners.add(tunnelListener)
    }

    override fun detach(view: View) {
        super.detach(view)
        this.view = null
        tunnelEvents.listeners.remove(tunnelListener)
    }

    private val tunnelListener = object : IEnabledStateActorListener {
        override fun startActivating() = appStateChanged(R.string.main_status_activating)
        override fun finishActivating() = appStateChanged(R.string.main_active)
        override fun startDeactivating() = appStateChanged(R.string.main_deactivating_new)
        override fun finishDeactivating() = appStateChanged(R.string.main_paused)
    }

    private val appStateChanged = { statusResId: Int ->
        view?.apply {
            val statusString = ctx.getBrandedString(statusResId)
            content = Slot.Content(statusString, statusString)
            date = Date()
        }
        Unit
    }

}

class InfoSlotVB(
        private val messageResId: Int,
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        view.type = Slot.Type.INFO
        val message = ctx.getBrandedString(messageResId)
        view.content = Slot.Content(
                label = message,
                header = ctx.getString(R.string.slot_message),
                description = message
        )
    }

}

class DroppedCountSlotVB(
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx
    private val tunnelEvents by lazy { ktx.di().instance<Tunnel>() }
    private var droppedCountListener: IWhen? = null

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        droppedCountListener = tunnelEvents.tunnelDropCount.doOnUiWhenSet().then {
            view.type = Slot.Type.COUNTER
            val message = ctx.getString(R.string.tunnel_dropped_count2,
                    Format.counter(tunnelEvents.tunnelDropCount()))
            view.content = Slot.Content(
                    label = message,
                    header = ctx.getString(R.string.slot_counter),
                    description = message,
                    action1 = Slot.Action("Share", {})
            )
        }
    }

    override fun detach(view: View) {
        super.detach(view)
        tunnelEvents.tunnelDropCount.cancel(droppedCountListener)
    }
}

class FiltersStatusSlotVB(
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx
    private var view: SlotView? = null

    private val updatingFilters = {
        view?.apply {
            type = Slot.Type.INFO
            val message = ctx.getString(R.string.tunnel_hosts_updating)
            content = Slot.Content(
                    label = message,
                    header = ctx.getString(R.string.slot_message),
                    description = message
            )
            date = Date()
        }
        Unit
    }

    private val rulesetBuilt = { it: Pair<Int, Int> ->
        view?.apply {
            type = Slot.Type.COUNTER
            val message = ctx.getString(R.string.slot_desc_ruleset_built, Format.counter(it.first))
            content = Slot.Content(
                    label = message,
                    header = ctx.getString(R.string.slot_counter),
                    description = message
            )
            date = Date()
        }
        Unit
    }

    override fun attach(view: View) {
        super.attach(view)
        this.view = view as SlotView
        ktx.on(Events.RULESET_BUILT, rulesetBuilt)
        ktx.on(Events.FILTERS_CHANGING, updatingFilters)
    }

    override fun detach(view: View) {
        super.detach(view)
        this.view = null
        ktx.cancel(Events.RULESET_BUILT, rulesetBuilt)
        ktx.cancel(Events.FILTERS_CHANGING, updatingFilters)
    }
}

class MemoryLimitSlotVB(
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx
    private var view: SlotView? = null

    private val memoryLimit = { it: Int ->
        view?.apply {
            type = Slot.Type.COUNTER
            val message = ctx.getString(R.string.tunnel_config_memory_capacity, Format.counter(it))
            content = Slot.Content(
                    label = message,
                    header = ctx.getString(R.string.slot_counter),
                    description = message
            )
        }
        Unit
    }

    override fun attach(view: View) {
        super.attach(view)
        this.view = view as SlotView
        ktx.on(Events.MEMORY_CAPACITY, memoryLimit)
    }

    override fun detach(view: View) {
        super.detach(view)
        this.view = null
        ktx.cancel(Events.MEMORY_CAPACITY, memoryLimit)
    }
}
class ForwardSlotVB(
        private val host: String,
        private val date: Date,
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        view.type = Slot.Type.FORWARD
        val message = ctx.getString(R.string.dashboard_forwarded, host)
        val description = ctx.getString(R.string.slot_desc_forward)
        view.content = Slot.Content(
            label = message,
            header = host,
            description = description,
            action1 = Slot.Action(ctx.getString(R.string.slot_action_block), {

            }),
            action2 = Slot.Action("Facts", {})
        )
        view.date = date
    }
}

class BlockSlotVB(
        private val host: String,
        private val date: Date,
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        view.type = Slot.Type.BLOCK
        val message = ctx.getString(R.string.dashboard_blocked, host)
        val description = ctx.getString(R.string.slot_desc_block)
        view.content = Slot.Content(
            label = message,
            header = host,
            description = description,
            action1 = Slot.Action(ctx.getString(R.string.slot_action_allow), {

            }),
            action2 = Slot.Action("Facts", {})
        )
        view.date = date
    }
}

class FilterVB(
        private val filter: Filter,
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx
    private val i18n by lazy { ktx.di().instance<I18n>() }
    private val filterManager by lazy { ktx.di().instance<tunnel.Main>() }

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        val name = filter.customName ?: i18n.localisedOrNull("filters_${filter.id}_name") ?: sourceToName(ctx, filter.source)
        val comment = filter.customComment ?: i18n.localisedOrNull("filters_${filter.id}_comment")

        view.enableAlternativeBackground()
        view.type = Slot.Type.INFO
        view.content = Slot.Content(
                label = name,
                header = name,
                description = comment,
                icon = ktx.ctx.getDrawable(R.drawable.ic_hexagon_multiple),
                switched = filter.active,
                detail = filter.source.source,
                action2 = Slot.Action("Author", {})
        )
        view.onSwitch = { on ->
            filterManager.putFilter(ktx, filter.copy(active = on))
        }
    }
}

class DownloadListsVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        view.enableAlternativeBackground()
        view.type = Slot.Type.INFO
        view.content = Slot.Content(
                label = i18n.getString(R.string.tunnel_config_refetch_now_title),
                header = i18n.getString(R.string.tunnel_config_refetch_now_title),
                description = i18n.getString(R.string.tunnel_config_refetch_now_description),
                icon = ktx.ctx.getDrawable(R.drawable.ic_download),
                action1 = Slot.Action(i18n.getString(R.string.tunnel_config_refetch_now), {

                })
        )
    }
}

class ListDownloadFrequencyVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        view.enableAlternativeBackground()
        view.type = Slot.Type.INFO
        view.content = Slot.Content(
                label = i18n.getString(R.string.tunnel_config_refetch_frequency_title),
                header = i18n.getString(R.string.tunnel_config_refetch_frequency_title),
                description = i18n.getString(R.string.tunnel_config_refetch_frequency_description),
                icon = ktx.ctx.getDrawable(R.drawable.ic_timer),
                values = listOf(
                        i18n.getString(R.string.tunnel_config_refetch_frequency_1),
                        i18n.getString(R.string.tunnel_config_refetch_frequency_2),
                        i18n.getString(R.string.tunnel_config_refetch_frequency_3),
                        i18n.getString(R.string.tunnel_config_refetch_frequency_4)
                ),
                selected = i18n.getString(R.string.tunnel_config_refetch_frequency_1)
        )
    }
}

class DownloadOnWifiVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        view.enableAlternativeBackground()
        view.type = Slot.Type.INFO
        view.content = Slot.Content(
                label = i18n.getString(R.string.tunnel_config_wifi_only_title),
                header = i18n.getString(R.string.tunnel_config_wifi_only_title),
                description = i18n.getString(R.string.tunnel_config_wifi_only_description),
                icon = ktx.ctx.getDrawable(R.drawable.ic_wifi),
                switched = true
        )
    }
}

class NewFilterVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val modal: ModalManager = modalManager
) : SlotVB() {

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        view.enableAlternativeBackground()
        view.type = Slot.Type.NEW
        view.content = Slot.Content(i18n.getString(R.string.slot_new_filter))
        view.onTap = {
            modal.openModal()
            ktx.ctx.startActivity(Intent(ktx.ctx, StepActivity::class.java))
        }
    }
}

class EnterDomainVB(
        private val ktx: AndroidKontext,
        private val accepted: (List<FilterSourceDescriptor>) -> Unit = {}
) : SlotVB(), Stepable {

    private var view: SlotView? = null

    private var input = ""
    private var inputValid = false

    private val validate = { input: String -> when {
        validateHostname(input) -> null
        validateSeveralHostnames(input) -> null
        validateURL(input) -> null
        else -> ktx.ctx.getString(R.string.slot_enter_domain_error)
    }}

    private fun validateHostname(it: String) = hostnameRegex.containsMatchIn(it.trim())
    private fun validateSeveralHostnames(it: String) = it.split(",").map { validateHostname(it) }.all { it }
    private fun validateURL(it: String) = try { URL(it); true } catch (e: Exception) { false }

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        this.view = view
        view.enableAlternativeBackground()
        view.type = Slot.Type.EDIT
        view.content = Slot.Content(ktx.ctx.getString(R.string.slot_enter_domain_title),
                description = ktx.ctx.getString(R.string.slot_enter_domain_desc),
                action1 = Slot.Action(ktx.ctx.getString(R.string.slot_continue), {
                    if (inputValid) {
                        view.fold()
                        val sources = when {
                            validateSeveralHostnames(input) -> {
                                input.split(",").map {
                                    FilterSourceDescriptor("single", it.trim())
                                }
                            }
                            validateHostname(input) -> listOf(FilterSourceDescriptor("single", input.trim()))
                            else -> listOf(FilterSourceDescriptor("link", input.trim()))
                        }
                        accepted(sources)
                    }
                }),
                action2 = Slot.Action(ktx.ctx.getString(R.string.slot_enter_domain_file), {})
        )

        view.onInput = { it ->
            input = it
            val error = validate(it)
            inputValid = error == null
            error
        }
    }

    override fun focus() {
        view?.unfold()
    }
}

class EnterNameVB(
        private val ktx: AndroidKontext,
        private val accepted: (String) -> Unit = {}
) : SlotVB(), Stepable {

    var generatedNameInput = ""

    private var view: SlotView? = null

    private var input = ""
    private var inputValid = false

    private val validate = { input: String -> when {
        input.isNotBlank() -> null
        else -> ktx.ctx.getString(R.string.slot_enter_name_error)
    }}

    private fun generateName(input: String) = when {
        input.isBlank() -> "A entry"
        else -> "An entry for $input"
    }

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        this.view = view
        view.enableAlternativeBackground()
        view.type = Slot.Type.EDIT
        view.content = Slot.Content(ktx.ctx.getString(R.string.slot_enter_name_title),
                description = ktx.ctx.getString(R.string.slot_enter_name_desc),
                action1 = Slot.Action(ktx.ctx.getString(R.string.slot_continue), {
                    if (inputValid) {
                        view.fold()
                        accepted(input)
                    }
                }),
                action2 = Slot.Action(ktx.ctx.getString(R.string.slot_enter_name_generate), {
                    view.input = generateName(generatedNameInput)
                })
        )

        view.onInput = { it ->
            input = it
            val error = validate(it)
            inputValid = error == null
            error
        }
    }

    override fun focus() {
        view?.unfold()
    }
}

class AppVB(
        private val app: Filter,
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        view.type = Slot.Type.APP
        val message = ctx.getString(R.string.slot_app_label, sourceToName(ctx, app.source))
        val description = ctx.getString(R.string.slot_app_desc)
        view.content = Slot.Content(
                label = message,
                header = sourceToName(ctx, app.source),
                description = description,
                detail = app.source.source,
                icon = sourceToIcon(ctx, app.source.source),
                action1 = Slot.Action(ctx.getString(R.string.slot_action_allow), {

                }),
                action2 = Slot.Action("Facts", {})
        )
    }
}

class AllAppVB(
        private val app: App,
        private val ktx: AndroidKontext,
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        view.enableAlternativeBackground()
        view.type = Slot.Type.APP
        val description = ctx.getString(R.string.slot_allapp_desc)
        view.content = Slot.Content(
                label = app.label,
                header = app.label,
                description = description,
                detail = app.appId,
                icon = sourceToIcon(ctx, app.appId),
                values = listOf(
                        ctx.getString(R.string.slot_allapp_whitelisted),
                        ctx.getString(R.string.slot_allapp_normal)
                ),
                selected = ctx.getString(R.string.slot_allapp_normal),
                action1 = Slot.Action(ctx.getString(R.string.slot_allapp_whitelist), {

                }),
                action2 = Slot.Action("Facts", {})
        )
    }
}

class DnsVB(
        private val dns: DnsChoice,
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val dnss: Dns = ktx.di().instance(),
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView
        view.enableAlternativeBackground()

        val id = if (dns.id.startsWith("custom")) "custom" else dns.id
        val name = i18n.localisedOrNull("dns_${id}_name") ?: id.capitalize()
        val description = dns.comment ?: i18n.localisedOrNull("dns_${id}_comment")

        val s = if (dns.servers.isNotEmpty()) dns.servers else dnss.dnsServers()
        val servers = printServers(s)

        view.type = Slot.Type.INFO
        view.content = Slot.Content(
                label = name,
                header = name,
                description = description,
                detail = servers,
                icon = ctx.getDrawable(R.drawable.ic_server),
                switched = dns.active
        )
        view.onSwitch = { switched ->
            if (!switched) {
                dnss.choices().first().active = true
            } else {
                dnss.choices().filter { it.active }.forEach { it.active = false }
            }
            dns.active = switched
            dnss.choices %= dnss.choices()
        }
    }
}

class DnsCurrentVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val dnss: Dns = ktx.di().instance(),
        private val slotMutex: SlotMutex = SlotMutex()
) : SlotVB(slotMutex) {

    private val ctx = ktx.ctx
    private var get: IWhen? = null

    override fun attach(view: View) {
        super.attach(view)
        view as SlotView

        get = dnss.dnsServers.doOnUiWhenSet().then {
            val name = ctx.getString(R.string.slot_dns_name, printServers(dnss.dnsServers()))
            view.type = Slot.Type.INFO
            view.content = Slot.Content(
                    label = name,
                    header = printServers(dnss.dnsServers()),
                    description = ctx.getString(R.string.slot_dns_dns),
                    icon = ctx.getDrawable(R.drawable.ic_server),
                    action1 = Slot.Action("Facts", {})
            )
        }
    }

    override fun detach(view: View) {
        super.detach(view)
        dnss.dnsServers.cancel(get)
    }
}
