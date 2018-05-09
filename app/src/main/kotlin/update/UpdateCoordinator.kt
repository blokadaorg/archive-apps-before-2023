package update

import com.github.salomonbrys.kodein.instance
import core.Tunnel
import core.TunnelState
import gs.environment.Environment
import gs.environment.Journal
import kotlinx.coroutines.experimental.android.UI
import java.net.URL

/**
 * It makes sure Blokada is inactive during update download.
 */
class UpdateCoordinator(
        private val xx: Environment,
        private val downloader: AUpdateDownloader,
        private val s: Tunnel = xx().instance(),
        private val j: Journal = xx().instance()
) {

    private var urls: List<URL> = emptyList()
    private var downloading = false

    private val downloadContinue = { it: TunnelState ->
        if (it == TunnelState.INACTIVE && !downloading) {
            j.log("UpdateCoordinator: tunnel deactivated")
            download(urls)
        }
    }

    fun start(urls: List<URL>) {
        if (downloading) return
        if (s.tunnelState() == TunnelState.INACTIVE ) {
            download(urls)
        }
        else {
            j.log("UpdateCoordinator: deactivate tunnel: ${s.tunnelState()}")
            this.urls = urls
            s.tunnelState.cancel(downloadContinue)
            s.tunnelState.onChange(UI, downloadContinue)

            s.updating %= true
            s.restart %= true
            s.active %= false
        }
    }

    private fun download(urls: List<URL>) {
        j.log("UpdateCoordinator: start download")
        s.tunnelState.cancel(downloadContinue)
        downloading = true
        downloader.downloadUpdate(urls, { uri ->
            j.log("UpdateCoordinator: downloaded: url $uri")
            if (uri != null) downloader.openInstall(uri)
            s.updating %= false
            downloading = false
        })
    }

}

