package com.zonik.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object DebugLog {
    private val entries = ConcurrentLinkedDeque<String>()
    private const val MAX_ENTRIES = 500
    private const val MAX_FILE_SIZE = 512 * 1024L // 512 KB
    private const val LOG_FILE = "debug_log.txt"
    private const val PREV_LOG_FILE = "debug_log_prev.txt"
    private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    private val dateTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
    private var logFile: File? = null
    private var initialized = false

    /**
     * Initialize file-based logging and install crash handler.
     * Call from Application.onCreate().
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val dir = context.filesDir
        logFile = File(dir, LOG_FILE)

        // Rotate if previous log is too large
        val file = logFile!!
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            val prev = File(dir, PREV_LOG_FILE)
            prev.delete()
            file.renameTo(prev)
            logFile = File(dir, LOG_FILE)
        }

        // Load previous session entries into memory for display
        loadFromFile()

        // Mark session start
        val sessionLine = "--- Session start ${dateTimeFormat.get()!!.format(Date())} ---"
        appendToFile(sessionLine)
        entries.addLast(sessionLine)

        // Install uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crash = buildString {
                    appendLine("!!! CRASH on thread '${thread.name}' !!!")
                    appendLine("${throwable.javaClass.name}: ${throwable.message}")
                    throwable.stackTrace.take(30).forEach { appendLine("    at $it") }
                    var cause = throwable.cause
                    while (cause != null) {
                        appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.take(15).forEach { appendLine("    at $it") }
                        cause = cause.cause
                    }
                }
                val time = timeFormat.get()!!.format(Date())
                val line = "$time E/CRASH: $crash"
                appendToFile(line)
            } catch (_: Exception) {
                // Best effort — don't make crash handling worse
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

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
        val time = timeFormat.get()!!.format(Date())
        val line = "$time $level/$tag: $message"
        entries.addLast(line)
        while (entries.size > MAX_ENTRIES) {
            entries.pollFirst()
        }
        appendToFile(line)
    }

    fun getAll(): String {
        return entries.joinToString("\n")
    }

    /**
     * Returns all persisted logs including previous sessions and crash data.
     */
    fun getPersistedLogs(): String {
        val file = logFile ?: return getAll()
        return try {
            if (file.exists()) file.readText() else getAll()
        } catch (_: Exception) {
            getAll()
        }
    }

    /**
     * Returns previous session's logs (useful for post-crash analysis).
     */
    fun getPreviousSessionLogs(): String? {
        val dir = logFile?.parentFile ?: return null
        val prev = File(dir, PREV_LOG_FILE)
        return try {
            if (prev.exists()) prev.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        entries.clear()
        logFile?.delete()
    }

    private fun appendToFile(line: String) {
        try {
            logFile?.appendText("$line\n")
        } catch (_: Exception) {
            // Best effort
        }
    }

    private fun loadFromFile() {
        try {
            val file = logFile ?: return
            if (!file.exists()) return
            val lines = file.readLines()
            // Load last MAX_ENTRIES lines into memory
            lines.takeLast(MAX_ENTRIES).forEach { entries.addLast(it) }
        } catch (_: Exception) {
            // Best effort
        }
    }
}
