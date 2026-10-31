package io.github.lichen.raybirdpush.core

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Register before blocking. Concurrent cancel closes existing and future resources. */
class Resources : Closeable {
    private val closed = AtomicBoolean(false)
    private val items = mutableListOf<Closeable>()
    val isClosed get() = closed.get()
    @Synchronized fun <T : Closeable> add(value: T): T {
        if (closed.get()) { runCatching { value.close() }; throw InterruptedException("已取消") }
        items.add(value)
        return value
    }
    @Synchronized fun remove(value: Closeable) { items.remove(value) }
    fun check() { if (closed.get() || Thread.currentThread().isInterrupted) throw InterruptedException("已取消") }
    override fun close() {
        val list = synchronized(this) {
            if (!closed.compareAndSet(false, true)) return
            items.toList().also { items.clear() }
        }
        list.asReversed().forEach { runCatching { it.close() } }
    }
}
