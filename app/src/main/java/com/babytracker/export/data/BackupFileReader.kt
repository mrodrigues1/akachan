package com.babytracker.export.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.babytracker.export.domain.InvalidBackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Opens the SAF document Uri and hands a size-capped [InputStream] to [parse], off the main
     * thread. The stream throws [InvalidBackupException] as soon as it reads past [MAX_BACKUP_BYTES],
     * so a caller that parses incrementally (e.g. Json.decodeFromStream) never buffers the whole file
     * — peak heap is the parsed object, not a String + ByteArray copy of the document.
     */
    suspend fun <T> readStreamed(uri: Uri, parse: (InputStream) -> T): T = withContext(Dispatchers.IO) {
        queryDeclaredSize(uri)?.let { ensureWithinLimit(it) }
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw InvalidBackupException("Could not open the selected backup file")
            stream.use { input -> parse(SizeLimitedInputStream(input, MAX_BACKUP_BYTES)) }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: java.io.IOException) {
            throw InvalidBackupException("Could not read the backup file: ${e.message}", e)
        } catch (e: SecurityException) {
            throw InvalidBackupException("No permission to read the selected file: ${e.message}", e)
        }
    }

    /**
     * Reads a SAF document Uri to a UTF-8 string off the main thread, rejecting anything larger than
     * [MAX_BACKUP_BYTES]. Retained for callers/tests that need the whole document as a String; the
     * import path uses [readStreamed] so it never materializes the full String.
     */
    suspend fun read(uri: Uri): String =
        readStreamed(uri) { input -> input.readBytes().toString(Charsets.UTF_8) }

    internal fun ensureWithinLimit(sizeBytes: Long) {
        if (sizeBytes > MAX_BACKUP_BYTES) {
            throw InvalidBackupException("Backup file is too large (> $MAX_BACKUP_BYTES bytes)")
        }
    }

    private fun queryDeclaredSize(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
            }

    /**
     * Caps how many bytes a caller can pull from [delegate]: once the running total exceeds [limit]
     * it throws [InvalidBackupException], so a streaming parse aborts instead of buffering an
     * over-sized document. Mirrors the incremental guard the old fully-buffered read performed.
     */
    private class SizeLimitedInputStream(
        private val delegate: InputStream,
        private val limit: Long,
    ) : InputStream() {
        private var total = 0L

        private fun track(bytesRead: Int): Int {
            if (bytesRead > 0) {
                total += bytesRead
                if (total > limit) {
                    throw InvalidBackupException("Backup file is too large (> $limit bytes)")
                }
            }
            return bytesRead
        }

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) track(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = track(delegate.read(b, off, len))

        override fun close() = delegate.close()
    }

    companion object {
        const val MAX_BACKUP_BYTES = 50L * 1024 * 1024 // 50 MiB
    }
}
