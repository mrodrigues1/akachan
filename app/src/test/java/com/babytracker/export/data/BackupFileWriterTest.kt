package com.babytracker.export.data

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupFileWriterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val writer = BackupFileWriter(context)

    /**
     * Robolectric 4.x shares the FileProvider class (and its static path-strategy cache) across
     * test methods in the same JVM. Each test gets a fresh Application with a different cacheDir,
     * so cached absolute roots from a previous test's cacheDir become invalid. Clearing the
     * static cache before each test forces FileProvider to re-resolve paths from the current
     * test's cacheDir.
     */
    @Before
    fun clearFileProviderCache() {
        val cacheField = FileProvider::class.java.getDeclaredField("sCache")
        cacheField.isAccessible = true
        (cacheField.get(null) as? MutableMap<*, *>)?.clear()
    }

    @Test
    fun `writeCacheFile returns a readable content uri with the given content`() = runTest {
        val uri = writer.writeCacheFile("backup.json", "{\"x\":1}")
        assertEquals("content", uri.scheme)
        val read = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals("{\"x\":1}", read)
    }

    @Test
    fun `two writes with the same name do not clobber each other`() = runTest {
        val first = writer.writeCacheFile("backup.json", "FIRST")
        val second = writer.writeCacheFile("backup.json", "SECOND")
        // Distinct Uris (unique per-export subdir) and the first is still intact after the second.
        assertTrue(first != second)
        val firstContent = context.contentResolver.openInputStream(first)!!
            .use { it.readBytes().decodeToString() }
        val secondContent = context.contentResolver.openInputStream(second)!!
            .use { it.readBytes().decodeToString() }
        assertEquals("FIRST", firstContent)
        assertEquals("SECOND", secondContent)
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
}
