package core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.github.salomonbrys.kodein.*
import filter.*
import gs.environment.*
import gs.property.*
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Properties

abstract class Filters {
    abstract val filters: Property<List<Filter>>
    abstract val filtersCompiled: Property<Set<String>>
    abstract val changed: Property<Boolean>
    abstract val uiChangeCounter: Property<Int>
    abstract val apps: Property<List<App>>

    // Those do not change during lifetime of the app
    abstract val filterConfig: Property<FilterConfig>
}

class FiltersImpl(
        private val xx: Environment,
        private val ctx: Context = xx().instance(),
        private val j: Journal = xx().instance()
) : Filters() {

    private val pages: Pages by xx.instance()

    override val filterConfig = Property.of({ ctx.inject().instance<FilterConfig>() })
    override val changed = Property.of({ false })
    override val uiChangeCounter = Property.of({ 0 })

    private val filtersRefresh = { it: List<Filter> ->
        j.log("filters: refresh: start ${pages.filters}")
        changed %= false
        val c = filterConfig()
        val serialiser: FilterSerializer = ctx.inject().instance()
        val builtinFilters = try {
            serialiser.deserialise(load({ openUrl(pages.filters(), c.fetchTimeoutMillis) }))
        } catch (e: Exception) {
            // We may make this request exactly while establishing VPN, erroring out. Simply wait a bit.
            Thread.sleep(3000)
            serialiser.deserialise(load({ openUrl(pages.filters(), c.fetchTimeoutMillis) }))
        }
        j.log("filters: refresh: downloaded")

        val newFilters = if (it.isEmpty()) {
            // First preselect
            builtinFilters
        } else {
            // Update existing filters just in case
            it.map { filter ->
                val newFilter = builtinFilters.find { it == filter }
                if (newFilter != null) {
                    newFilter.active = filter.active
                    newFilter.localised = filter.localised
                    newFilter.hidden = filter.hidden
                    newFilter
                } else filter
            }.plus(builtinFilters.minus(it))
        }

        // Try to fetch localised copy for filters if available
        j.log("filters: refresh: fetch localisation")
        val prop = Properties()
        prop.load(InputStreamReader(openUrl(pages.filtersStrings(), c.fetchTimeoutMillis), Charset.forName("UTF-8")))
        newFilters.forEach { try {
            it.localised = LocalisedFilter(
                    name = prop.getProperty("${it.id}_name")!!,
                    comment = prop.getProperty("${it.id}_comment")
            )
        } catch (e: Exception) {}}

        j.log("filters: refresh: finish")
        changed %= true
        newFilters
    }

    private val appsRefresh = {
        j.log("filters: apps: start")
        val installed = ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val a = installed.map {
            App(
                    appId = it.packageName,
                    label = ctx.packageManager.getApplicationLabel(it).toString(),
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedBy { it.label }
        j.log("filters: apps: found ${a.size} apps")
        a
    }

    override val apps = Property.of({ appsRefresh() }, refresh = { appsRefresh() },
            shouldRefresh = { it.isEmpty() })

    override val filters = Property.ofPersisted(
            persistence = AFiltersPersistence(xx = xx, apps = apps, default = { emptyList() }),
            zeroValue = { emptyList() },
            refresh = filtersRefresh,
            shouldRefresh = {
                val c = filterConfig()
                val now = ctx.inject().instance<Time>().now()
                when {
                    !isCacheValid(c.cacheFile, c.cacheTTLMillis, now) -> true
                    it.isEmpty() -> true
                // TODO: maybe check if we have connectivity (assuming we can trust it)
                    else -> false
                }
            },
            name = "filters"
    )

    override val filtersCompiled = Property.ofPersisted(
            persistence = ACompiledFiltersPersistence(xx),
            zeroValue = { emptySet() },
            refresh = {
                j.log("filters: compile: start")
                changed %= false

                // Drop any not-selected filters to free memory
                filters().filter { !it.active }.forEach {
                    it.hosts = emptyList()
                }

                try {
                    val selected = filters().filter(Filter::active)
                    j.log("filters: compile: downloading")
                    downloadFilters(selected)
                    val selectedBlacklist = selected.filter { !it.whitelist }
                    val selectedWhitelist = selected.filter(Filter::whitelist)

                    j.log("filters: compile: combining")
                    val c = combine(selectedBlacklist, selectedWhitelist)
                    j.log("filters: compile: finish")
                    c
                } catch (e: Exception) {
                    j.log("filters: compile: fail", e)
                    changed %= true
                    it
                }
            },
            shouldRefresh = {
                val c = filterConfig()
                val now = ctx.inject().instance<Time>().now()
                when {
                    changed() -> true
                    !isCacheValid(c.cacheFile, c.cacheTTLMillis, now) -> true
                    it.isEmpty() -> true
                    else -> false
                }
            },
            name = "filtersCompiled"
    )

}

internal fun downloadFilters(filters: List<Filter>) {
    filters.forEach { filter ->
        if (filter.hosts.isEmpty()) {
            filter.hosts = filter.source.fetch()
        }
    }
}

internal fun combine(blacklist: List<Filter>, whitelist: List<Filter>): Set<String> {
    val set = mutableSetOf<String>()
    blacklist.forEach { set.addAll(it.hosts) }
    whitelist.forEach { set.removeAll(it.hosts) }
    return set
}

fun newFiltersModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Filters>() with singleton { FiltersImpl(xx = lazy,
                ctx = ctx) }
        bind<IHostlineProcessor>() with singleton { DefaultHostlineProcessor() }
        bind<IFilterSource>() with factory { sourceId: String ->
            val cfg: FilterConfig = instance()
            val processor: IHostlineProcessor = instance()

            when (sourceId) {
                "link" -> FilterSourceLink(cfg.fetchTimeoutMillis, processor)
                "file" -> FilterSourceUri(ctx = instance(), processor = instance())
                "app" -> FilterSourceApp(ctx = instance(), j = instance())
                else -> FilterSourceSingle()
            }}
        bind<FilterSerializer>() with singleton {
            FilterSerializer(repo = instance(),
                    sourceProvider = { type: String -> with(type).instance<IFilterSource>() })
        }
        bind<FilterConfig>() with singleton {
            FilterConfig(
                    cacheFile = File(getPersistencePath(ctx).absoluteFile, "filters"),
                    exportFile = getPublicPersistencePath("blokada-export")
                            ?: File(getPersistencePath(ctx).absoluteFile, "blokada-export"),
                    cacheTTLMillis = 1 * 24 * 60 * 60 * 100L, // A
                    fetchTimeoutMillis = 10 * 1000
            )
        }
        bind<AFilterAddDialog>() with provider {
            AFilterAddDialog(ctx,
                    sourceProvider = { type: String -> with(type).instance<IFilterSource>() }
            )
        }
        bind<AFilterGenerateDialog>(true) with provider {
            AFilterGenerateDialog(ctx,
                    s = instance(),
                    sourceProvider = { type: String -> with(type).instance<IFilterSource>() },
                    whitelist = true
            )
        }
        bind<AFilterGenerateDialog>(false) with provider {
            AFilterGenerateDialog(ctx,
                    s = instance(),
                    sourceProvider = { type: String -> with(type).instance<IFilterSource>() },
                    whitelist = false
            )
        }
        onReady {
            val s: Filters = instance()
            val t: Tunnel = instance()
            val j: Journal = instance()

            // Reload engine in case whitelisted apps selection changes
            var currentApps = listOf<Filter>()
            s.changed.onChange {
                val newApps = s.filters().filter { it.whitelist && it.active && it.source is FilterSourceApp }
                if (newApps != currentApps) {
                    currentApps = newApps

                    if (!t.enabled()) {
                    } else if (t.active()) {
                        t.restart %= true
                        t.active %= false
                    } else {
                        t.retries.refresh()
                        t.restart %= false
                        t.active %= true
                    }
                }
            }

            // Compile filters every time they change
            s.changed.onChange {
                if (s.changed()) {
                    j.log("filters: compiled: refresh ping")
                    s.filtersCompiled.refresh(recheck = true)
                }
            }

            // Push filters to engine every time they're changed
            val engine: IEngineManager = instance()
            s.filtersCompiled.onChange {
                engine.updateFilters()
            }

            // On locale change, refresh all localised content
            val i18n: I18n = instance()

            i18n.locale.onChange {
                j.log("refresh filters from locale change")
                s.filters.refresh()
            }

            // Refresh filters list whenever system apps switch is changed
            val ui: UiState = instance()
            ui.showSystemApps.onChange {
                s.uiChangeCounter %= s.uiChangeCounter() + 1
            }
        }
    }
}

data class Filter(
        val id: String,
        val source: IFilterSource,
        val credit: String? = null,
        var active: Boolean = false,
        var whitelist: Boolean = false,
        var hosts: List<String> = emptyList(),
        var localised: LocalisedFilter? = null,
        var hidden: Boolean = false
) {

    override fun hashCode(): Int {
        return source.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Filter) return false
        return source.equals(other.source)
    }
}

data class App(
        val appId: String,
        val label: String,
        val system: Boolean
)

data class LocalisedFilter(
        val name: String,
        val comment: String? = null
)


data class FilterConfig(
        val cacheFile: File,
        val exportFile: File,
        val cacheTTLMillis: Long,
        val fetchTimeoutMillis: Int
)

class AFiltersPersistence(
        val xx: Environment,
        val apps: Property<List<App>>,
        val ctx: Context = xx().instance(),
        val j: Journal = xx().instance(),
        val s: FilterSerializer = xx().instance(),
        val default: () -> List<Filter>
) : Persistence<List<Filter>> {

    val p by lazy { ctx.getSharedPreferences("filters", Context.MODE_PRIVATE) }

    override fun read(current: List<Filter>): List<Filter> {
        return try {
            j.log("FiltersPersistence: read")
            val filters = s.deserialise(p.getString("filters", "").split("^"))
            j.log("FiltersPersistence: read: ${filters.size} loaded")
            if (filters.isNotEmpty()) filters else default()
        } catch (e: Exception) {
            current
        }
    }

    override fun write(source: List<Filter>) {
        val e = p.edit()
        e.putInt("migratedVersion", 20)
        e.putString("filters", s.serialise(source).joinToString("^"))
        e.apply()
    }

}

interface IFilterSource {
    fun fetch(): List<String>
    fun fromUserInput(vararg string: String): Boolean
    fun toUserInput(): String
    fun serialize(): String
    fun deserialize(string: String, version: Int): IFilterSource
    fun id(): String
}


class ACompiledFiltersPersistence(
        val xx: Environment,
        val ctx: Context = xx().instance(),
        val j: Journal = xx().instance(),
        val cfg: FilterConfig = xx().instance()
) : Persistence<Set<String>> {

    private val cache by lazy { cfg.cacheFile }

    override fun read(current: Set<String>): Set<String> {
        return try {
            j.log("compiledFiltersPersistence: start")
            val c = readFromCache(cache).toSet()
            j.log("compiledFiltersPersistence: finish")
            c
        } catch (e: Exception) {
            j.log("compiledFiltersPersistence: fail", e)
            setOf()
        }
    }

    override fun write(source: Set<String>) {
        saveToCache(source, cache)
    }

}

