package com.tyust.course.ui.system.glass

internal data class GlassFrameRequest<T>(val params: T, val sourceRevision: Long)

/** Coalesces UI and source updates without using completed frames as a render clock. */
internal class GlassFrameQueue<T> {
    private var params: T? = null
    private var revision = 0L
    private var completed: GlassFrameRequest<T>? = null
    private var scheduled = false
    private var closed = false

    @Synchronized
    fun request(params: T): Boolean {
        if (closed) return false
        this.params = params
        return schedule()
    }

    @Synchronized
    fun sourceChanged(revision: Long): Boolean {
        this.revision = maxOf(this.revision, revision)
        return schedule()
    }

    @Synchronized
    fun next(): GlassFrameRequest<T>? = if (closed) null else current()

    @Synchronized
    fun finish(request: GlassFrameRequest<T>, rendered: Boolean): Boolean {
        if (rendered) completed = request
        val again = !closed && current() != request && current() != completed
        scheduled = again
        return again
    }

    @Synchronized
    fun close() {
        closed = true
        params = null
        completed = null
        scheduled = false
    }

    private fun current(): GlassFrameRequest<T>? = params?.let { GlassFrameRequest(it, revision) }

    private fun schedule(): Boolean {
        if (closed || scheduled || params == null || current() == completed) return false
        scheduled = true
        return true
    }
}
