package me.xechoz.loga

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * A single background worker that processes submitted tasks in FIFO order.
 *
 * [shutdown] blocks until the queue is drained, guaranteeing that no log data
 * is lost on release. A failing task is swallowed so that it cannot kill the
 * worker and silently drop every subsequent task.
 */
internal class Worker {
    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val consumer = scope.launch {
        for (task in queue) {
            try {
                task()
            } catch (_: Throwable) {
            }
        }
    }

    fun submit(task: () -> Unit) {
        queue.trySend(task)
    }

    /**
     * Submits [task] and blocks until it has run, i.e. until all previously
     * submitted tasks have completed.
     */
    fun submitAndWait(task: () -> Unit) {
        val done = CompletableDeferred<Unit>()
        queue.trySend {
            try {
                task()
            } finally {
                done.complete(Unit)
            }
        }
        runBlocking { done.await() }
    }

    /**
     * Stops accepting tasks and blocks until all queued tasks have run.
     */
    fun shutdown() {
        queue.close()
        runBlocking { consumer.join() }
    }
}
