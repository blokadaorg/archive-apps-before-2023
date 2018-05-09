package core

import android.content.Context
import com.github.salomonbrys.kodein.*
import com.github.salomonbrys.kodein.Kodein.Module
import gs.environment.Environment
import gs.environment.Journal
import gs.environment.Worker
import gs.environment.inject
import gs.obsolete.hasCompleted
import gs.property.Device
import gs.property.IWatchdog
import gs.property.Property
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import tunnel.checkTunnelPermissions

abstract class Tunnel {
    abstract val enabled: Property<Boolean>
    abstract val retries: Property<Int>
    abstract val active: Property<Boolean>
    abstract val restart: Property<Boolean>
    abstract val updating: Property<Boolean>
    abstract val tunnelState: Property<TunnelState>
    abstract val tunnelPermission: Property<Boolean>
    abstract val tunnelDropCount: Property<Int>
    abstract val tunnelRecentDropped: Property<List<String>>
    abstract val tunnelConfig: Property<TunnelConfig>
    abstract val startOnBoot: Property<Boolean>
}

class TunnelImpl(
        private val xx: Environment,
        private val ctx: Context = xx().instance()
) : Tunnel() {

    override val tunnelConfig = Property.of({ ctx.inject().instance<TunnelConfig>() })
    override val enabled = Property.ofPersisted({ false }, APrefsPersistence(ctx, "enabled"))
    override val active = Property.ofPersisted({ false }, APrefsPersistence(ctx, "active"))
    override val restart = Property.ofPersisted({ false}, APrefsPersistence(ctx, "restart"))
    override val retries = Property.of({ 3 })
    override val updating = Property.of({ false })
    override val tunnelState = Property.of({ TunnelState.INACTIVE })
    override val tunnelPermission = Property.of({
        val (completed, _) = hasCompleted(null, { checkTunnelPermissions(ctx) })
        completed
    })
    override val tunnelDropCount = Property.ofPersisted({ 0 }, APrefsPersistence(ctx, "tunnelAdsCount"))
    override val tunnelRecentDropped = Property.of({ listOf<String>() })
    override val startOnBoot  = Property.ofPersisted({ true }, APrefsPersistence(ctx, "startOnBoot"))
}

fun newTunnelModule(ctx: Context): Module {
    return Module {
        bind<Tunnel>() with singleton { TunnelImpl(xx = lazy) }
        bind<TunnelConfig>() with singleton { TunnelConfig(defaultEngine = "lollipop") }
        bind<IPermissionsAsker>() with singleton {
            object : IPermissionsAsker {
                override fun askForPermissions() {
                    MainActivity.askPermissions()
                }
            }
        }
        onReady {
            val s: Tunnel = instance()
            val d: Device = instance()
            val j: Journal = instance()
            val engine: IEngineManager = instance()
            val perms: IPermissionsAsker = instance()
            val watchdog: IWatchdog = instance()
            val retryKctx: Worker = with("retry").instance()

            // React to user switching us off / on
            s.enabled.onChange {
                s.restart %= s.enabled() && (s.restart() || d.isWaiting())
                s.active %= s.enabled() && !d.isWaiting()
            }

            // The tunnel setup routine (with permissions request)
            s.active.onChange {
                if (s.active() && s.tunnelState() == TunnelState.INACTIVE) {
                    s.retries %= s.retries() - 1
                    s.tunnelState %= TunnelState.ACTIVATING
                    runBlocking {
                        s.tunnelPermission.refresh()?.join()
                        if (!s.tunnelPermission()) {
                            hasCompleted(j, {
                                perms.askForPermissions()
                            })
                            s.tunnelPermission.refresh()?.join()
                        }
                        if (s.tunnelPermission()) {
                            val (completed, err) = hasCompleted(null, { engine.start() })
                            if (completed) {
                                s.tunnelState %= TunnelState.ACTIVE
                            } else {
                                j.log(Exception("tunnel: could not activate", err))
                            }
                        }
                    }

                    if (s.tunnelState() != TunnelState.ACTIVE) {
                        s.tunnelState %= TunnelState.DEACTIVATING
                        hasCompleted(j, { engine.stop() })
                        s.tunnelState %= TunnelState.DEACTIVATED
                    }

                    s.updating %= false
                }
            }

            // Things that happen after we get everything set up nice and sweet
            var resetRetriesTask: Deferred<*>? = null

            s.tunnelState.onChange {
                if (s.tunnelState() == TunnelState.ACTIVE) {
                    // Make sure the tunnel is actually usable by checking connectivity
                    if (d.screenOn()) watchdog.start()
                    if (resetRetriesTask != null) resetRetriesTask?.cancel()

                    // Reset retry counter in case we seem to be stable
                    resetRetriesTask = async(retryKctx) {
                        if (s.tunnelState() == TunnelState.ACTIVE) {
                            Thread.sleep(15 * 1000)
                            j.log("tunnel: stable")
                            if (s.tunnelState() == TunnelState.ACTIVE) s.retries.refresh()
                        }
                    }
                }
            }

            // Things that happen after we get the tunnel off
            s.tunnelState.onChange {
                if (s.tunnelState() == TunnelState.DEACTIVATED) {
                    s.active %= false
                    s.restart %= true
                    s.tunnelState %= TunnelState.INACTIVE
                    if (resetRetriesTask != null) resetRetriesTask?.cancel()

                    // Monitor connectivity if disconnected, in case we can't relay on Android event
                    if (s.enabled() && d.screenOn()) watchdog.start()

                    // Reset retry counter after a longer break since we never give up, never surrender
                    resetRetriesTask = async(retryKctx) {
                        if (s.enabled() && s.retries() == 0 && s.tunnelState() != TunnelState.ACTIVE) {
                            delay(5 * 1000)
                            if (s.enabled() && s.tunnelState() != TunnelState.ACTIVE) {
                                j.log("tunnel: restart after wait")
                                s.retries.refresh()
                                s.restart %= true
                                s.tunnelState %= TunnelState.INACTIVE
                            }
                        }
                    }
                }
            }

            // Turn off the tunnel if disabled (by user, no connectivity, or giving up on error)
            s.active.onChange {
                if (!s.active()
                        && s.tunnelState() in listOf(TunnelState.ACTIVE, TunnelState.ACTIVATING)) {
                    watchdog.stop()
                    s.tunnelState %= TunnelState.DEACTIVATING
                    hasCompleted(j, { engine.stop() })
                    s.tunnelState %= TunnelState.DEACTIVATED
                }
            }

            // Auto off in case of no connectivity, and auto on once connected
            d.connected.onChange {
                when {
                    !d.connected() && s.active() -> {
                        j.log("tunnel: no connectivity, deactivating")
                        s.restart %= true
                        s.active %= false
                    }
                    d.connected() && s.restart() && !s.updating() && s.enabled() -> {
                        j.log("tunnel: connectivity back, activating")
                        s.restart %= false
                        s.active %= true
                    }
                }
            }

            // Auto restart (eg. when reconfiguring the engine, or retrying)
            s.tunnelState.onChange {
                if (s.tunnelState() == TunnelState.INACTIVE && s.enabled() && s.restart() && !s.updating()
                            && !d.isWaiting() && s.retries() > 0) {
                    j.log("tunnel: auto restart")
                    s.restart %= false
                    s.active %= true
                }
            }

            // Make sure watchdog is started and stopped as user wishes
            d.watchdogOn.onChange { when {
                d.watchdogOn() && s.tunnelState() in listOf(TunnelState.ACTIVE, TunnelState.INACTIVE) -> {
                    // Flip the connected flag so we detect the change if now we're actually connected
                    d.connected %= false
                    watchdog.start()
                }
                !d.watchdogOn() -> {
                    watchdog.stop()
                    d.connected.refresh()
                }
            }}

            // Monitor connectivity only when user is interacting with device
            d.screenOn.onChange { when {
                !s.enabled() -> Unit
                d.screenOn() && s.tunnelState() in listOf(TunnelState.ACTIVE, TunnelState.INACTIVE) -> watchdog.start()
                !d.screenOn() -> watchdog.stop()
            }}
        }
    }
}

enum class TunnelState {
    INACTIVE, ACTIVATING, ACTIVE, DEACTIVATING, DEACTIVATED
}

open class Engine (
        val id: String,
        val supported: Boolean = true,
        val recommended: Boolean = false,
        val createIEngineManager: (e: EngineEvents) -> IEngineManager
)

data class EngineEvents (
        val adBlocked: (String) -> Unit = {},
        val error: (String) -> Unit = {},
        val onRevoked: () -> Unit = {}
)

data class TunnelConfig(
        val defaultEngine: String
)

interface IEngineManager {
    fun start()
    fun updateFilters()
    fun stop()
}

interface IPermissionsAsker {
    fun askForPermissions()
}

