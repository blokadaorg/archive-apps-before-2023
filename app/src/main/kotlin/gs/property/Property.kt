package gs.property

import android.util.Log
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.properties.Delegates

class Property<T> private constructor(
        private val zeroValue: () -> T,
        private val refresh: (value: T) -> T,
        private val shouldRefresh: (value: T) -> Boolean,
        private val name: String
) {

    private val queue: ConcurrentLinkedQueue<Pair<CoroutineContext, (T) -> Unit>> = ConcurrentLinkedQueue()

    private var refreshing: Job? = null
        @Synchronized get
        @Synchronized set

    private var value by Delegates.observable(null as T?, { prop, old, new ->
        if (new != null && new != old) changed(new)
    })

    private fun value(): T {
        if (value == null) value = zeroValue()
        return value!!
    }
    operator fun invoke(): T {
        if (shouldRefresh(value())) refresh(recheck = true)
        return value()
    }

    operator fun remAssign(value: T) {
        this.value = value
    }

    fun onChange(cctx: CoroutineContext = DefaultDispatcher, job: (T) -> Unit): Property<T> {
        if (!queue.contains(cctx to job)) queue.add(cctx to job)
        if (value != null) launch(cctx) { job(value!!) }
        return this
    }

    fun cancel(job: (T) -> Unit, cctx: CoroutineContext = DefaultDispatcher) {
        queue.remove(cctx to job)
    }

    fun refresh(recheck: Boolean = false): Job {
        return if (refreshing?.isActive ?: false) refreshing!!
        else {
            refreshing?.cancel()
            refreshing = launch {
                try {
                    if (!recheck || shouldRefresh(value())) {
                        Log.e("gscore", "$name property: refreshing")
                        value = refresh(value())
                    }
                } catch (e: Exception) {
                    Log.e("gscore", "$name property: refresh fail: ${e}", e)
                }
            }
            refreshing!!
        }
    }

    fun changed(new: T = value!!) {
        queue.forEach ({ launch(it.first) { it.second(new) }})
    }

    override fun toString(): String {
        return value.toString()
    }

    companion object {
        fun <T> of(zeroValue: () -> T, refresh: (v: T) -> T = { it },
                   shouldRefresh: (v: T) -> Boolean = { true},
                   name: String = "unnamed"): Property<T> {
            return Property(zeroValue, refresh, shouldRefresh, name)
        }

        fun <T> ofPersisted(zeroValue: () -> T, persistence: Persistence<T>,
                            refresh: (v: T) -> T = { it },
                            shouldRefresh: (v: T) -> Boolean = { false },
                            name: String = "unnamed-persisted"): Property<T> {
            val p = Property({ persistence.read(zeroValue()) }, refresh, shouldRefresh, name)
            p.onChange {
                persistence.write(it)
            }
            return p
        }
    }
}

