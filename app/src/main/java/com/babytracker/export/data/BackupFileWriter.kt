package com.babytracker.export.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Writes UTF-8 bytes to the given SAF Uri off the main thread. No durability
     * guarantee here: callers (PR4) must obtain the Uri from ACTION_CREATE_DOCUMENT so
     * the write targets a freshly created document, never an existing backup. Any
     * failure propagates — do not treat a thrown exception as success.
     */
    suspend fun writeToUri(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        // "wt" = write + TRUNCATE. The Uri always comes from ACTION_CREATE_DOCUMENT (a fresh,
        // empty doc), so truncation is harmless there; it also guarantees that if this is ever
        // handed a non-empty document, a shorter backup cannot leave stale trailing bytes that
        // would corrupt a later JSON import.
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Could not open output stream for $uri")
    }

    /**
     * Transient path: write to cache off the main thread, return a shareable FileProvider Uri.
     *
     * Each call writes into a fresh UUID subdirectory of exports/, so a later export can never
     * overwrite the file behind a Uri whose share/receive is still pending — yet the recipient
     * still sees the human-readable [fileName]. The OS evicts the cache dir over time, so old
     * artifacts are reclaimed automatically (no explicit cleanup needed here).
     */
    suspend fun writeCacheFile(fileName: String, content: String): Uri =
        writeCacheBytes(fileName, content.toByteArray(Charsets.UTF_8))

    /** Transient path (binary, e.g. PDF): write to cache, return a shareable FileProvider Uri. */
    suspend fun writeCacheBytes(fileName: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        // Reject path separators / relative-dir names before touching the filesystem: a value
        // like "../foo" or an absolute path could otherwise escape the export dir and truncate
        // an app-private file (FileProvider only validates AFTER the write).
        require(
            !fileName.contains('/') && !fileName.contains('\\') &&
                fileName != "." && fileName != "..",
        ) { "Invalid export file name: $fileName" }

        val uniqueDir = File(File(context.cacheDir, "exports"), java.util.UUID.randomUUID().toString())
            .apply { mkdirs() }
        val file = File(uniqueDir, fileName)
        // Defense in depth: the resolved file must sit directly inside its unique dir.
        require(file.canonicalFile.parentFile == uniqueDir.canonicalFile) {
            "Resolved export path escapes exports dir: $fileName"
        }
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
