/*
 * This file is part of Blokada.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright © 2021 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package utils

import android.util.Log
import service.LogService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

open class Logger(private val component: String) {

    open fun e(message: String) = Logger.e(component, message)
    open fun w(message: String) = Logger.w(component, message)
    open fun v(message: String) = Logger.v(component, message)

    companion object {

        private val dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.of("UTC"))

        fun e(component: String, message: String) {
            saveToFile(6, component, message)
            logcatLine(6, component, message)
        }

        fun w(component: String, message: String) {
            saveToFile(5, component, message)
            logcatLine(5, component, message)
        }

        fun v(component: String, message: String) {
            saveToFile(2, component, message)
            logcatLine(2, component, message)
        }

        private fun logcatLine(priority: Int, component: String, message: String) {
            Log.println(priority, component, message)
        }

        fun saveToFile(priority: Int, component: String, message: String) {
            val p = when (priority) {
                6 -> "E"
                5 -> "W"
                else -> " "
            }
            val date = dateFormat.format(Instant.now())
            val line = "$date $p ${component.padEnd(10).slice(0..9)} $message"
            LogService.logToFile(line)
        }
    }

}

class LoggerWithThread(val component: String) : Logger(component) {

    override fun e(message: String) = super.e(thread() + message)
    override fun w(message: String) = super.w(thread() + message)
    override fun v(message: String) = super.v(thread() + message)

    private fun thread() = "{${Thread.currentThread().id}} "

}