package core

import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.instance
import kotlinx.coroutines.experimental.android.UI

/**
 * Translates internal MainState changes into higher level events used by topbar and fab.
 */
class EnabledStateActor(
        val di: LazyKodein,
        val s: Tunnel = di().instance(),
        val listeners: MutableList<IEnabledStateActorListener> = mutableListOf()
) {

    private val listener = { it: Any ->
        update(s)
    }

    init {
        s.enabled.onChange(UI, listener)
        s.active.onChange(UI, listener)
        s.tunnelState.onChange(UI, listener)
    }

    fun update(s: Tunnel) {
        when {
            s.tunnelState() == TunnelState.ACTIVATING -> startActivating()
            s.tunnelState() == TunnelState.DEACTIVATING -> startDeactivating()
            s.tunnelState() == TunnelState.ACTIVE -> finishActivating()
            s.active() -> startActivating()
            else -> finishDeactivating()
        }
    }

    private fun startActivating() {
        try { listeners.forEach { it.startActivating() } } catch (e: Exception) {}
    }

    private fun finishActivating() {
        try { listeners.forEach { it.finishActivating() } } catch (e: Exception) {}
    }

    private fun startDeactivating() {
        try { listeners.forEach { it.startDeactivating() } } catch (e: Exception) {}
    }

    private fun finishDeactivating() {
        try { listeners.forEach { it.finishDeactivating() } } catch (e: Exception) {}
    }
}

interface IEnabledStateActorListener {
    fun startActivating()
    fun finishActivating()
    fun startDeactivating()
    fun finishDeactivating()
}
