package com.zonik.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object DebugLog {
    private val entries = ConcurrentLinkedDeque<String>()
    private const val MAX_ENTRIES = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        add("D", tag, message)
        android.util.Log.d(tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        val msg = if (error != null) "$message: ${error.message}" else message
        add("E", tag, msg)
        android.util.Log.e(tag, message, error)
    }

    fun w(tag: String, message: String) {
        add("W", tag, message)
        android.util.Log.w(tag, message)
    }

    private fun add(level: String, tag: String, message: String) {
        val time = timeFormat.format(Date())
        entries.addLast("$time $level/$tag: $message")
        while (entries.size > MAX_ENTRIES) {
            entries.pollFirst()
        }
    }

    fun getAll(): String {
        return entries.joinToString("\n")
    }

    fun clear() {
        entries.clear()
    }
}
