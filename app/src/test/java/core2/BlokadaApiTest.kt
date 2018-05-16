package core2

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test

class BlokadaApiTest {

    var tunnelCrash = 3

    @Test fun hello_api() {
        val cmd = TunnelOperator(
                getPermissions = { cmd -> true },
                askPermissions = { cmd -> runBlocking {
                    runBlocking { delay(1000) }
                    cmd.send(true)
                }},
                startTunnel = { cmd -> runBlocking {
                    runBlocking { delay(1000) }
                    if (tunnelCrash-- > 0) throw Exception("kurwa, tunel jebnal")
                }},
                stopTunnel = { cmd -> runBlocking {
                    runBlocking { delay(1000) }
                }}
        )

        runBlocking {
            monitor("tunnel", cmd)

            val retries = TunnelKeeper(cmd)
            monitor("keeper", retries)
            retries.send(TunnelKeeper.ON)

            val c = cmd.send(TunnelOperator.START)
//            val a = handleChannel("main", c)
//            a.join()

            delay(25000)
//            cmd.send(TunnelOperator.STOP)
            retries.send(TunnelKeeper.OFF)

            delay(50000)
        }
    }

}

fun monitor(name: String, cmd: Operator) {
    val m = cmd.send(DefaultOperator.ADD_MONITOR)
    handleChannel(name, m)
}

fun handleChannel(name: String, c: Pipe) = launch {
    for (value in c) { when {
        value is Exception -> {
            System.err.println("$name: ${value.message}")
//            value.printStackTrace()
        }
        else -> {
            System.out.println("$name: $value")
        }
    }}
}
