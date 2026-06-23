package com.babytracker.export.data

import androidx.test.core.app.ApplicationProvider
import com.babytracker.export.domain.InvalidBackupException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupFileReaderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val reader = BackupFileReader(context)

    @Test
    fun `reads UTF-8 text from a uri`() = runTest {
        val file = File(context.cacheDir, "in.json").apply { writeText("{\"k\":1}") }
        val result = reader.read(android.net.Uri.fromFile(file))
        assertEquals("{\"k\":1}", result)
    }

    @Test
    fun `readStreamed hands a readable stream to the parser`() = runTest {
        val file = File(context.cacheDir, "in-stream.json").apply { writeText("hello-world") }
        val text = reader.readStreamed(android.net.Uri.fromFile(file)) {
            it.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals("hello-world", text)
    }

    @Test
    fun `ensureWithinLimit rejects oversized declared size`() {
        assertThrows(InvalidBackupException::class.java) {
            reader.ensureWithinLimit(BackupFileReader.MAX_BACKUP_BYTES + 1)
        }
    }

    @Test
    fun `ensureWithinLimit accepts a size at the limit`() {
        reader.ensureWithinLimit(BackupFileReader.MAX_BACKUP_BYTES) // does not throw
    }
}
