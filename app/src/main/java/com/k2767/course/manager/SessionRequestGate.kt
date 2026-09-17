package com.k2767.course.manager

data class SessionRequestTicket(val session: SessionToken, val channel: String, val sequence: Long)

/** An older request must not finish a newer spinner, even within the same session. */
class SessionRequestGate(private val sessions: SessionStateStore) {
    private var sequence = 0L
    private val latest = mutableMapOf<String, SessionRequestTicket>()
    @Synchronized fun begin(channel: String): SessionRequestTicket =
        SessionRequestTicket(sessions.token, channel, ++sequence).also { latest[channel] = it }
    @Synchronized fun isCurrent(ticket: SessionRequestTicket): Boolean =
        sessions.isCurrent(ticket.session) && latest[ticket.channel] == ticket
    @Synchronized fun isCurrent(channel: String): Boolean = latest[channel]?.let(::isCurrent) == true
    @Synchronized fun cancelAll() { latest.clear() }
    @Synchronized fun cancel(channel: String) { latest.remove(channel) }
}
