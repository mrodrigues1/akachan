package com.babytracker.export.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupFileWriterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val writer = BackupFileWriter(context)
    private val providedFiles = mutableListOf<File>()

    /**
     * AndroidX FileProvider's JVM path check uses '/' as the root separator. On Windows,
     * File.canonicalPath uses '\', so valid cache files are rejected before the writer can return.
     * Keep these tests focused on the writer's behavior and mock the static provider boundary.
     */
    @Before
    fun setupFileProvider() {
        providedFiles.clear()
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } answers {
            val file = thirdArg<File>()
            providedFiles += file
            Uri.parse("content://com.babytracker.fileprovider/${file.parentFile?.name}/${file.name}")
        }
    }

    @After
    fun tearDownFileProvider() {
        unmockkStatic(FileProvider::class)
    }

    @Test
    fun `writeCacheFile returns a readable content uri with the given content`() = runTest {
        val uri = writer.writeCacheFile("backup.json", "{\"x\":1}")
        assertEquals("content", uri.scheme)
        assertEquals("{\"x\":1}", providedFiles.single().readText())
    }

    @Test
    fun `two writes with the same name do not clobber each other`() = runTest {
        val first = writer.writeCacheFile("backup.json", "FIRST")
        val second = writer.writeCacheFile("backup.json", "SECOND")
        // Distinct Uris (unique per-export subdir) and the first is still intact after the second.
        assertTrue(first != second)
        assertEquals("FIRST", providedFiles[0].readText())
        assertEquals("SECOND", providedFiles[1].readText())
    }

    @Test
    fun `writeToUri writes bytes to the given stream`() = runTest {
        val target = File(context.cacheDir, "saf-target.json")
        writer.writeToUri(android.net.Uri.fromFile(target), "hello")
        assertEquals("hello", target.readText())
    }

    @Test
    fun `writeToUri truncates a longer existing target leaving no stale bytes`() = runTest {
        val target = File(context.cacheDir, "saf-overwrite.json")
        val uri = android.net.Uri.fromFile(target)
        writer.writeToUri(uri, "AAAAAAAAAAAAAAAAAAAA") // 20 chars
        writer.writeToUri(uri, "BBB")                   // 3 chars
        assertEquals("BBB", target.readText())          // no trailing "AAAA..." remains
    }

    @Test
    fun `writeToUri propagates error when stream cannot be opened`() = runTest {
        val bogus = android.net.Uri.parse("content://com.babytracker.nonexistent/doc/1")
        // The suspend call is invoked inside this lambda; the exception surfaces synchronously
        // within runTest's scope.
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { writer.writeToUri(bogus, "data") }
        }
    }

    @Test
    fun `writeCacheFile rejects path traversal and absolute names`() = runTest {
        for (bad in listOf("../escape.json", "nested/dir.json", "/abs/path.json", "..")) {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { writer.writeCacheFile(bad, "x") }
            }
        }
        // The traversal target must not have been created.
        assertTrue(!File(context.cacheDir, "escape.json").exists())
    }

    @Test
    fun `writeCacheBytes returns readable content uri and rejects traversal`() = runTest {
        val uri = writer.writeCacheBytes("report.pdf", byteArrayOf(37, 80, 68, 70)) // %PDF
        assertEquals("content", uri.scheme)
        val read = providedFiles.single().readBytes()
        assertEquals("%PDF", read.decodeToString())

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { writer.writeCacheBytes("../evil.pdf", byteArrayOf(1)) }
        }
    }
}
