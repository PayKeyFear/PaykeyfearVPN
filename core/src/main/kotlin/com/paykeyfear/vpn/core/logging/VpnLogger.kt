package com.paykeyfear.vpn.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Entry point for app-wide logging.
 *
 * - In debug builds, plants a [Timber.DebugTree] so logs show up in Logcat.
 * - Always plants an in-memory [RingBufferTree] so the UI's Logs screen can
 *   show recent events without relying on `logcat` access (which requires
 *   adb on modern Android).
 */
object VpnLogger {
    /** Max entries kept in memory. Sized to fit comfortably in one screen worth of scroll. */
    const val BUFFER_SIZE: Int = 500

    private val ring = RingBufferTree(BUFFER_SIZE)

    /** Observable snapshot of the most recent log entries (newest last). */
    val entries: StateFlow<List<LogEntry>> get() = ring.entries

    fun install(debug: Boolean) {
        if (Timber.forest().none { it is RingBufferTree }) {
            Timber.plant(ring)
        }
        if (debug && Timber.forest().none { it is Timber.DebugTree }) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun snapshot(): List<LogEntry> = ring.entries.value

    fun clear() = ring.clear()
}

data class LogEntry(
    val epochMs: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val throwable: Throwable? = null,
)

/**
 * Timber tree that retains the most recent [capacity] log entries in a
 * bounded ring buffer, emitting a fresh snapshot on every write.
 * Tested on the JVM (no Android runtime needed).
 */
internal class RingBufferTree(private val capacity: Int) : Timber.Tree() {
    private val lock = Any()
    private val buffer: ArrayDeque<LogEntry> = ArrayDeque(capacity)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val entry = LogEntry(
            epochMs = System.currentTimeMillis(),
            priority = priority,
            tag = tag,
            message = message,
            throwable = t,
        )
        val snapshot: List<LogEntry>
        synchronized(lock) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(entry)
            snapshot = buffer.toList()
        }
        _entries.value = snapshot
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        _entries.value = emptyList()
    }
}
