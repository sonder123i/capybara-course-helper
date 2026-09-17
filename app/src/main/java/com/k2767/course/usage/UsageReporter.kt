package com.k2767.course.usage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class UsagePreferences(val noticeSeen: Boolean = false, val enabled: Boolean = true)

internal data class UsageRecord(
    val preferences: UsagePreferences = UsagePreferences(),
    val installationId: String? = null,
    val lastSuccessDay: String? = null
)

internal interface UsageStore {
    fun read(): UsageRecord
    fun write(record: UsageRecord)
}

internal fun interface UsageTransport {
    /** A successful acknowledgement contains the server's Beijing calendar day. */
    suspend fun report(installationId: String, version: String): String
}

internal object UsageDay {
    const val DAY_MILLIS = 86_400_000L
    private const val OFFSET = 8 * 3_600_000L

    fun at(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("GMT+08:00")
    }.format(Date(time))

    fun untilNextDay(time: Long): Long =
        (DAY_MILLIS - Math.floorMod(time + OFFSET, DAY_MILLIS)).coerceAtLeast(1L)

    fun isValid(day: String): Boolean = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(day) && runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        format.format(requireNotNull(format.parse(day))) == day
    }.getOrDefault(false)
}

/** Main-thread coordinator. No background job or account identifier is needed. */
internal class UsageReporter(
    private val store: UsageStore,
    private val transport: UsageTransport,
    private val version: String,
    private val eligibleBuild: Boolean,
    private val isDemo: () -> Boolean,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private var record = store.read()
    private val mutablePreferences = MutableStateFlow(record.preferences)
    val preferences: StateFlow<UsagePreferences> = mutablePreferences.asStateFlow()
    private var foreground = false
    private var storageAvailable = true
    private var generation = 0L
    private var job: Job? = null

    fun acknowledgeNotice(enabled: Boolean) {
        cancelPending()
        persist(record.copy(preferences = UsagePreferences(noticeSeen = true, enabled = enabled)))
        restart()
    }

    fun setEnabled(enabled: Boolean) {
        cancelPending()
        persist(record.copy(preferences = record.preferences.copy(enabled = enabled)))
        restart()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        restart()
    }

    fun refreshEligibility() = restart()

    private fun canReport(): Boolean = foreground && storageAvailable && eligibleBuild &&
        !isDemo() && record.preferences.noticeSeen && record.preferences.enabled

    private fun cancelPending() {
        generation++
        job?.cancel()
        job = null
    }

    private fun persist(next: UsageRecord): Boolean {
        record = next
        mutablePreferences.value = next.preferences
        storageAvailable = runCatching { store.write(next) }.isSuccess
        return storageAvailable
    }

    private fun restart() {
        cancelPending()
        if (!canReport()) return
        val ticket = generation
        job = scope.launch {
            while (currentCoroutineContext().isActive && canReport() && ticket == generation) {
                if (record.lastSuccessDay != UsageDay.at(now())) {
                    val id = record.installationId ?: newId().also {
                        // Never send an ID that could not be saved across app updates.
                        if (!persist(record.copy(installationId = it))) return@launch
                    }
                    try {
                        val acknowledgedDay = transport.report(id, version)
                        currentCoroutineContext().ensureActive()
                        if (!canReport() || ticket != generation) return@launch
                        if (UsageDay.isValid(acknowledgedDay)) {
                            if (!persist(record.copy(lastSuccessDay = acknowledgedDay))) return@launch
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the same ID. A later foreground visit retries a failed request.
                    }
                }
                delay(UsageDay.untilNextDay(now()) + 250L)
            }
        }
    }
}
