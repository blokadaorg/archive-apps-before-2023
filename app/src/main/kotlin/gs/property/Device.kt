package gs.property

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.github.salomonbrys.kodein.*
import gs.environment.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import java.net.InetSocketAddress
import java.net.Socket

abstract class Device {
    abstract val appInForeground: Property<Boolean>
    abstract val screenOn: Property<Boolean>
    abstract val connected: Property<Boolean>
    abstract val tethering: Property<Boolean>
    abstract val watchdogOn: Property<Boolean>

    fun isWaiting(): Boolean {
        return !connected()
    }
}

class DeviceImpl (
        xx: Environment,
        ctx: Context = xx().instance(),
        j: Journal = xx().instance()
) : Device() {

    private val pm: PowerManager by xx.instance()
    private val watchdog: IWatchdog by xx.instance()

    override val appInForeground = Property.of({ false })
    override val screenOn = Property.of({ pm.isInteractive })
    override val connected = Property.of({
        val c = isConnected(ctx) or watchdog.test()
        j.log("device: connected: ${c}")
        c
    })
    override val tethering = Property.of({ isTethering(ctx)})
    override val watchdogOn = Property.ofPersisted({ true }, BasicPersistence(xx, "watchdogOn"))
}

class ConnectivityReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        launch(ctx.inject().with("ConnectivityReceiver").instance()) {
            // Do it async so that Android can refresh the current network info before we access it
            val j: Journal = ctx.inject().instance()
            j.log("ConnectivityReceiver: ping")
            val s: Device = ctx.inject().instance()
            s.connected.refresh()
        }
    }

    companion object {
        fun register(ctx: Context) {
            val filter = IntentFilter()
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            ctx.registerReceiver(ctx.inject().instance<ConnectivityReceiver>(), filter)
        }

    }

}

fun newDeviceModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Device>() with singleton {
            DeviceImpl(xx = lazy)
        }
        bind<ConnectivityReceiver>() with singleton { ConnectivityReceiver() }
        bind<ScreenOnReceiver>() with singleton { ScreenOnReceiver() }
        bind<LocaleReceiver>() with singleton { LocaleReceiver() }
        bind<IWatchdog>() with singleton { AWatchdog(ctx) }
        onReady {
            // Register various Android listeners to receive events
            launch {
//                 In a task because we are in DI and using DI can lead to stack overflow
                ConnectivityReceiver.register(ctx)
                ScreenOnReceiver.register(ctx)
                LocaleReceiver.register(ctx)
            }
        }
    }
}

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        launch(ctx.inject().with("ScreenOnReceiver").instance()) {
            // This causes everything to load
            val j: Journal = ctx.inject().instance()
            j.log("ScreenOnReceiver: ping")
            val s: Device = ctx.inject().instance()
            s.screenOn.refresh()
        }
    }

    companion object {
        fun register(ctx: Context) {
            // Register ScreenOnReceiver
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            ctx.registerReceiver(ctx.inject().instance<ScreenOnReceiver>(), filter)
        }

    }
}

class LocaleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        launch(ctx.inject().with("LocaleReceiver").instance()) {
            val j: Journal = ctx.inject().instance()
            j.log("LocaleReceiver: ping")
            val i18n: I18n = ctx.inject().instance()
            i18n.locale.refresh()
        }
    }

    companion object {
        fun register(ctx: Context) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_LOCALE_CHANGED)
            ctx.registerReceiver(ctx.inject().instance<LocaleReceiver>(), filter)
        }

    }
}

interface IWatchdog {
    fun start()
    fun stop()
    fun test(): Boolean
}

/**
 * AWatchdog is meant to test if device has Internet connectivity at this moment.
 *
 * It's used for getting connectivity state since Android's connectivity event cannot always be fully
 * trusted. It's also used to test if Blokada is working properly once activated (and periodically).
 */
class AWatchdog(
        private val ctx: Context
) : IWatchdog {

    private val d by lazy { ctx.inject().instance<Device>() }
    private val j by lazy { ctx.inject().instance<Journal>() }
    private val kctx by lazy { ctx.inject().with("watchdog").instance<Worker>() }

    override fun test(): Boolean {
        if (!d.watchdogOn()) return true
        val socket = Socket()
        socket.soTimeout = 3000
        return try { socket.connect(InetSocketAddress("cloudflare.com", 80), 3000); true }
        catch (e: Exception) { false } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private val MAX = 120
    private var started = false
    private var wait = 1
    private var nextTask: Deferred<*>? = null

    @Synchronized override fun start() {
        if (started) return
        if (!d.watchdogOn()) { return }
        started = true
        wait = 1
        if (nextTask != null) nextTask?.cancel()
        nextTask = tick()
    }

    @Synchronized override fun stop() {
        started = false
        if (nextTask != null) nextTask?.cancel()
        nextTask = null
    }

    private fun tick(): Deferred<*> {
        return async(kctx) {
            if (started) {
                // Delay the first check to not cause false positives
                if (wait == 1) Thread.sleep(1000L)
                val connected = test()
                val next = if (connected) wait * 2 else wait
                wait *= 2
                if (d.connected() != connected) {
                    // Connection state change will cause reactivating (and restarting watchdog)
                    j.log("watchdog change: connected: $connected")
                    d.connected %= connected
                    stop()
                } else {
                    Thread.sleep(Math.min(next, MAX) * 1000L)
                    nextTask = tick()
                }
            }
        }
    }
}
