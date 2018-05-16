package core2

import android.app.Activity
import core.IEngineManager
import core.TunnelState
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.selects.whileSelect

fun putTogether(
        act: Activity,
        engine: IEngineManager
): TunnelOperator {
    return TunnelOperator(
            getPermissions = { act.getPermissions() },
            askPermissions = { act.startAskTunnelPermissions(it) },
            startTunnel = { engine.start() },
            stopTunnel = { engine.stop() }
    )
}

class TunnelKeeper(
        private val tunnel: TunnelOperator
): Operator {

    companion object {
        private val DEFAULT_RETRIES = 3

        val ON = Command("ON", laneIndex = 2)
        val OFF = Command("OFF")
        private val RETRY_LATER = Command("RETRY_LATER", laneIndex = 3)
        private val CHECK_STABILITY = Command("CHECK_STABILITY", laneIndex = 3)
    }

    val operator = DefaultOperator(
            ON to { c -> run(c) },
            OFF to { c -> stop(c) },
            RETRY_LATER to { c -> retryAfterWait(c) },
            CHECK_STABILITY to { c -> checkStabilityAfterWait(c) },
            workerLanes = 3
    )

    private var tunnelPipe: Pipe? = null
    private var waitPipe: Pipe? = null
    private var retries = DEFAULT_RETRIES

    private fun run(pipe: Pipe) = runBlocking {
        tunnelPipe = tunnel.send(DefaultOperator.ADD_MONITOR)
        for (msg in tunnelPipe!!) {
            when (msg) {
                TunnelState.INACTIVE -> {
                    waitPipe?.close()
                    if (--retries > 0) {
                        pipe.send("keeper: will retry now")
                        tunnel.send(TunnelOperator.START)
                    }
                    else {
                        pipe.send("keeper: will wait")
                        waitPipe = send(RETRY_LATER)
                    }
                }
                TunnelState.ACTIVE -> {
                    waitPipe = send(CHECK_STABILITY)
                }
                is TunnelState -> {
                    if (waitPipe?.isClosedForReceive ?: true == false) {
                        pipe.send("keeper: tunnel state changed, cancelling wait")
                        waitPipe?.close()
                    }
                }
            }
        }
    }

    private fun stop(pipe: Pipe) = runBlocking {
        tunnelPipe?.close()
        waitPipe?.close()
        retries = DEFAULT_RETRIES
    }

    private fun retryAfterWait(pipe: Pipe) = runBlocking {
        val retry = async {
            delay(15 * 1000)
            pipe.send("keeper: will retry now after wait")
            retries = DEFAULT_RETRIES - 1
            tunnel.send(TunnelOperator.START)
        }

        try {
            whileSelect {
                retry.onAwait { throw Exception("keeper: waiting done") }
                waitPipe!!.onReceiveOrNull { if (it == null)
                    throw Exception("keeper: cancelling long wait retry")
                    true
                }
            }
        } catch (e: Exception) {
            pipe.send(e)
            retry.cancel()
        }
    }

    private fun checkStabilityAfterWait(pipe: Pipe) = runBlocking {
        val stable = async {
            delay(15 * 1000)
            pipe.send("keeper: tunnel stable")
            retries = DEFAULT_RETRIES
        }

        try {
            whileSelect {
                stable.onAwait { throw Exception("keeper: stability waiting done") }
                waitPipe!!.onReceiveOrNull { if (it == null)
                    throw Exception("keeper: cancelling stability check")
                    true
                }
            }
        } catch (e: Exception) {
            pipe.send(e)
            stable.cancel()
        }
    }

    override fun send(msg: Message): Pipe {
        return operator.send(msg)
    }
}


class TunnelOperator(
        val getPermissions: BooleanExecutor,
        val askPermissions: Executor,
        val startTunnel: Executor,
        val stopTunnel: Executor
) : Operator {

    companion object {
        val START = Command("START")
        val STOP = Command("STOP")
        val PERMISSION_GET = Command("PERMISSION_GET")
        val PERMISSION_ASK = Command("PERMISSION_ASK")
    }

    val operator = DefaultOperator(
            START to { c -> start(c) },
            STOP to { c -> stop(c) },
            PERMISSION_ASK to askPermissions,
            PERMISSION_GET to getPermissions
    )

    var started = false

    private fun start(pipe: Pipe) = runBlocking {
        if (started) throw Exception("tunnel: already started")

        pipe.send(TunnelState.ACTIVATING)
        if (!getPermissions(pipe)) {
            pipe.send("tunnel: asking for permissions")
            askPermissions(pipe)
        }

        if (!getPermissions(pipe)) {
            pipe.send(TunnelState.INACTIVE)
            throw Exception("tunnel: could not get permissions")
        } else try {
            startTunnel(pipe)
            started = true
            pipe.send(TunnelState.ACTIVE)
        } catch (e: Exception) {
            pipe.send(TunnelState.DEACTIVATING)
            stopTunnel(pipe)
            pipe.send(TunnelState.INACTIVE)
            throw Exception("tunnel: could not start tunnel", e)
        }
    }

    private fun stop(pipe: Pipe) = runBlocking {
        if (!started) throw Exception("tunnel: already stopped")
        else {
            pipe.send(TunnelState.DEACTIVATING)
            stopTunnel(pipe)
            started = false
            pipe.send(TunnelState.INACTIVE)
        }
    }

    override fun send(msg: Message): Pipe {
        return operator.send(msg)
    }
}

class DefaultOperator(
        vararg executors: Pair<Message, Executor>,
        val workerLanes: Int = 1
) : Operator {

    companion object {
        val ADD_MONITOR = Command("ADD_MONITOR", laneIndex = 0)
        val REMOVE_MONITOR = Command("REMOVE_MONITOR", laneIndex = 0)
    }

    private val exec = executors.toMap()
    private val lanes = IntRange(0, workerLanes).map { Channel<Pair<Message, Pipe>>() }
    private val monitors = mutableListOf<Pipe>()

    init {
        launch {
            for ((msg, pipe) in lanes[0]) {
                when(msg) {
                    ADD_MONITOR -> {
                        monitors.add(pipe)
                        sendToMonitors("operator: added monitor: $pipe")
                    }
                    REMOVE_MONITOR -> {
                        monitors.remove(pipe)
                        sendToMonitors("operator: removed monitor: $pipe")
                        pipe.close()
                    }
                    else -> sendToMonitors(msg)
                }
            }
        }

        repeat(lanes.size - 1) {
            handleLane(it + 1)
        }
    }

    private fun handleLane(laneIndex: Int) = launch {
        launch {
            for ((msg, pipe) in lanes[laneIndex]) {
                val localPipe = newPipe()
                try {
                    when(msg) {
                        !exec.containsKey(msg) -> throw Exception("operator: unknown command: $msg")
                        else -> {
                            launch {
                                for (forwardMsg in localPipe) {
                                    lanes[0].send(forwardMsg to pipe)
                                    launch { pipe.send(forwardMsg) }
                                }
                                pipe.close()
                            }

                            localPipe.send("operator: executing: $msg")
                            localPipe.send(exec[msg]!!(localPipe))
                            localPipe.send("operator: finished: $msg")
                            localPipe.close()
                        }
                    }
                } catch (e: Exception) {
                    localPipe.send(e)
                }
            }
        }
    }

    private fun sendToMonitors(msg: Message) {
        monitors.forEach { pipe ->
            launch {
                try {
                    pipe.send(msg)
                } catch (e: Exception) {
                    lanes[0].send(REMOVE_MONITOR to pipe)
                }
            }
        }
    }

    override fun send(msg: Message) = {
        val pipe = newPipe()
        runBlocking {
            when(msg) {
                is Command -> lanes[msg.laneIndex].send(msg to pipe)
                else -> lanes[1].send(msg to pipe)
            }
        }
        pipe
    }()
}

data class Command(val name: String, val laneIndex: Int = 1)

interface Operator {
    fun send(msg: Message): Pipe
}

private fun newPipe(): Pipe {
    return Channel()
}

typealias BooleanExecutor = (Pipe) -> Boolean
typealias Executor = (Pipe) -> Any
typealias Pipe = Channel<Message>
typealias Message = Any

