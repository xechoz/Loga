package me.xechoz.loga

/**
 * A single background worker that processes submitted tasks in FIFO order.
 *
 * Implemented per platform so that [shutdown] can block until the queue is
 * drained, guaranteeing that no log data is lost on release.
 */
internal expect class Worker() {
    fun submit(task: () -> Unit)

    /**
     * Submits [task] and blocks until it has run, i.e. until all previously
     * submitted tasks have completed.
     */
    fun submitAndWait(task: () -> Unit)

    /**
     * Stops accepting tasks and blocks until all queued tasks have run.
     */
    fun shutdown()
}
