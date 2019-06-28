package core.bits

import android.content.Context
import android.content.Intent
import com.github.salomonbrys.kodein.instance
import core.*
import gs.property.I18n
import gs.property.IWhen
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig

class AdsBlockedVB(
        private val ktx: AndroidKontext,
        private val i18n: I18n = ktx.di().instance(),
        private val tunnelEvents: Tunnel = ktx.di().instance(),
        private val tunnelStatus: EnabledStateActor = ktx.di().instance()
) : ByteVB() {

    private var droppedCountListener: IWhen? = null
    private var dropped: Int = 0
    private var active = false
    private var activating = false
    private var config: BlockaConfig = BlockaConfig()

    override fun attach(view: ByteView) {
        droppedCountListener = tunnelEvents.tunnelDropCount.doOnUiWhenSet().then {
            dropped = tunnelEvents.tunnelDropCount()
            update()
        }
        tunnelStatus.listeners.add(tunnelListener)
        tunnelStatus.update(tunnelEvents)
        ktx.on(BLOCKA_CONFIG, configListener)
        update()
    }

    override fun detach(view: ByteView) {
        tunnelEvents.tunnelDropCount.cancel(droppedCountListener)
        tunnelStatus.listeners.remove(tunnelListener)
        ktx.cancel(BLOCKA_CONFIG, configListener)
    }

    private val update = {
        view?.run {
            when {
                !tunnelEvents.enabled() -> {
                    icon(null)
                    label("Touch to turn on".res())
                    state("Blokada is disabled".res())
                    arrow(null)
                    onTap {
                        tunnelEvents.enabled %= true
                    }
                    onArrowTap { }
                }
                activating -> {
                    icon(null)
                    label("Activating".res())
                    state("please wait...".res())
                    arrow(null)
                    onTap { }
                    onArrowTap { }
                }
                !config.adblocking -> {
                    icon(R.drawable.ic_power.res(), color = R.color.colorAccent.res())
                    label("VPN only mode".res())
                    state("ad blocking is disabled".res())
                    arrow(null)
                    onTap {
                        tunnelEvents.enabled %= false
                    }
                    onArrowTap {
                    }
                }
                else -> {
                    val droppedString = Format.counter(dropped)
                    icon(R.drawable.ic_power.res(), color = R.color.colorAccent.res())
                    label(droppedString.res())
                    state("requests blocked".res())
                    arrow("Share".res())
                    onTap {
                        tunnelEvents.enabled %= false
                    }
                    onArrowTap {
                        val shareIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, getMessage(ktx.ctx,
                                    tunnelEvents.tunnelDropStart(), tunnelEvents.tunnelDropCount()))
                            type = "text/plain"
                        }
                        ktx.ctx.startActivity(Intent.createChooser(shareIntent,
                                ktx.ctx.getText(R.string.slot_dropped_share_title)))
                    }
                }
           }
        }
        Unit
    }

    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        Unit
    }

    private val tunnelListener = object : IEnabledStateActorListener {
        override fun startActivating() {
            activating = true
            active = false
            update()
        }

        override fun finishActivating() {
            activating = false
            active = true
            update()
        }

        override fun startDeactivating() {
            activating = true
            active = false
            update()
        }

        override fun finishDeactivating() {
            activating = false
            active = false
            update()
        }
    }

    private fun getMessage(ctx: Context, timeStamp: Long, dropCount: Int): String {
        var elapsed: Long = System.currentTimeMillis() - timeStamp
        elapsed /= 60000
        if(elapsed < 120) {
            return ctx.resources.getString(R.string.social_share_bodym, dropCount, elapsed)
        }
        elapsed /= 60
        if(elapsed < 48) {
            return ctx.resources.getString(R.string.social_share_bodyh, dropCount, elapsed)
        }
        elapsed /= 24
        if(elapsed < 28) {
            return ctx.resources.getString(R.string.social_share_bodyd, dropCount, elapsed)
        }
        elapsed /= 7
        return ctx.resources.getString(R.string.social_share_bodyw, dropCount, elapsed)
    }

}
