package com.k2767.course.usage

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.BuildConfig
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in deployment smoke test. Never runs in normal builds or with a real installation's ID. */
@RunWith(AndroidJUnit4::class)
class UsageLiveServiceDeviceTest {
    @Test fun foregroundReporterReachesLiveServiceWithOnePersistentAnonymousId() = runBlocking {
        assumeTrue(BuildConfig.UI_PREVIEW &&
            InstrumentationRegistry.getArguments().getString("usageLiveCheck") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val filename = "usage-live-" + id + ".properties"
        val store = NoBackupUsageStore(context, filename)
        store.write(UsageRecord(installationId = id))
        // Print before sending, so a server-accepted/client-timeout record can also be cleaned up.
        println("USAGE_LIVE_INSTALLATION_ID=" + id)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val transport = HttpUsageTransport()
        val acknowledged = CompletableDeferred<String>()
        val reports = AtomicInteger()
        val reporter = withContext(Dispatchers.Main) {
            UsageReporter(store, UsageTransport { installationId, version ->
                reports.incrementAndGet()
                transport.report(installationId, version).also { acknowledged.complete(it) }
            }, BuildConfig.VERSION_NAME, eligibleBuild = true, isDemo = { false }, scope = scope)
        }
        try {
            withContext(Dispatchers.Main) {
                reporter.acknowledgeNotice(true)
                reporter.setForeground(true)
            }
            val day = withTimeout(25_000) { acknowledged.await() }
            withContext(Dispatchers.Main) {
                val persisted = NoBackupUsageStore(context, filename).read()
                assertEquals(id, persisted.installationId)
                assertEquals(day, persisted.lastSuccessDay)
                reporter.setForeground(false)
                reporter.setForeground(true)
            }
            assertEquals("A successful foreground revisit must not report twice", 1, reports.get())
            assertTrue(UsageDay.isValid(day))
            // Exercise server idempotency with the same test ID; the D1 check must still find one row.
            assertTrue(UsageDay.isValid(transport.report(id, BuildConfig.VERSION_NAME)))
            println("USAGE_LIVE_ACK_DAY=" + day)
        } finally {
            withContext(Dispatchers.Main) { reporter.setForeground(false) }
            scope.cancel()
            AtomicFile(File(context.noBackupFilesDir, filename)).delete()
        }
    }
}
