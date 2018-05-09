package core

import android.content.Context
import com.github.salomonbrys.kodein.*
import gs.environment.Environment
import gs.environment.Journal
import gs.environment.Worker
import gs.property.I18n
import gs.property.Property
import java.net.HttpURLConnection
import java.net.URL

abstract class Pages {
    abstract val loaded: Property<Boolean>
    abstract val intro: Property<URL>
    abstract val updated: Property<URL>
    abstract val obsolete: Property<URL>
    abstract val download: Property<URL>
    abstract val cleanup: Property<URL>
    abstract val patron: Property<URL>
    abstract val patronAbout: Property<URL>
    abstract val cta: Property<URL>
    abstract val donate: Property<URL>
    abstract val news: Property<URL>
    abstract val help: Property<URL>
    abstract val feedback: Property<URL>
    abstract val changelog: Property<URL>
    abstract val credits: Property<URL>
    abstract val filters: Property<URL>
    abstract val filtersStrings: Property<URL>
    abstract val chat: Property<URL>
    abstract val dns: Property<URL>
    abstract val dnsStrings: Property<URL>
}
class PagesImpl (
        w: Worker,
        xx: Environment,
        j: Journal = xx().instance()
) : Pages() {

    val i18n: I18n by xx.instance()

    init {
        i18n.locale.onChange {
            val c = i18n.contentUrl()
            if (!c.startsWith("http://localhost")) {
                j.log("pages: locale set: contentUrl: $c")
                intro %= URL("$c/intro.html")
                updated %= URL("$c/updated.html")
                cleanup %= URL("$c/cleanup.html")
                patronAbout %= URL("$c/patron.html")
                cta %= URL("$c/cta.html")
                donate %= URL("$c/donate.html")
                help %= URL("$c/help.html")
                changelog %= URL("$c/changelog.html")
                credits %= URL("$c/credits.html")
                filters %= URL("$c/filters.txt")
                filtersStrings %= URL("$c/filters.properties")
                dns %= URL("$c/dns.txt")
                dnsStrings %= URL("$c/dns.properties")
                patron %= resolveRedirect(patron())
                chat %= if (i18n.locale().startsWith("es")) {
                    URL("http://go.blokada.org/es_chat")
                } else URL("http://go.blokada.org/chat")

                loaded %= true
            }
        }
    }

    override val loaded = Property.of({ false })
    override val intro = Property.of({ URL("http://localhost") })
    override val updated = Property.of({ URL("http://localhost") })
    override val patronAbout = Property.of({ URL("http://localhost") })
    override val cleanup = Property.of({ URL("http://localhost") })
    override val cta = Property.of({ URL("http://localhost") })
    override val donate = Property.of({ URL("http://localhost") })
    override val help = Property.of({ URL("http://localhost") })
    override val changelog = Property.of({ URL("http://localhost") })
    override val credits = Property.of({ URL("http://localhost") })
    override val filters = Property.of({ URL("http://localhost") })
    override val filtersStrings = Property.of({ URL("http://localhost") })
    override val dns = Property.of({ URL("http://localhost") })
    override val dnsStrings = Property.of({ URL("http://localhost") })
    override val chat = Property.of({ URL("http://go.blokada.org/chat") })

    override val news = Property.of({ URL("http://go.blokada.org/news") })
    override val feedback = Property.of({ URL("http://go.blokada.org/feedback") })
    override val patron = Property.of({ URL("http://go.blokada.org/patron_redirect") })
    override val obsolete = Property.of({ URL("https://blokada.org/api/legacy/content/en/obsolete.html") })
    override val download = Property.of({ URL("https://blokada.org/#download") })

}

fun newPagesModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Pages>() with singleton {
            PagesImpl(with("gscore").instance(), lazy)
        }
    }
}
private fun resolveRedirect(url: URL): URL {
    return try {
        val ucon = url.openConnection() as HttpURLConnection
        ucon.setInstanceFollowRedirects(false)
        URL(ucon.getHeaderField("Location"))
    } catch (e: Exception) {
        url
    }
}
