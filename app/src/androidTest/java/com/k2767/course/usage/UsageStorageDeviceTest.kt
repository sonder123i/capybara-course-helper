package com.k2767.course.usage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UsageStorageDeviceTest {
    @Test fun installationIdentityAndOptOutLiveOnlyInNoBackupStorageAndSurviveReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "usage-test-${UUID.randomUUID()}.properties"
        val file = File(context.noBackupFilesDir, name)
        try {
            val store = NoBackupUsageStore(context, name)
            assertEquals(UsageRecord(), store.read())
            val record = UsageRecord(UsagePreferences(true, false), UUID.randomUUID().toString(), "2026-09-11")
            store.write(record)
            assertTrue(file.isFile)
            assertEquals(record, NoBackupUsageStore(context, name).read())
            assertFalse(File(context.filesDir, name).exists())
            assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
            file.writeText("invalid data")
            assertEquals(UsageRecord(), NoBackupUsageStore(context, name).read())
        } finally {
            file.delete()
            File(file.path + ".bak").delete()
            File(file.path + ".new").delete()
        }
    }
}
