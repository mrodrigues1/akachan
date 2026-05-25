package com.babytracker.export.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.babytracker.export.domain.InvalidBackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Reads a SAF document Uri to a UTF-8 string off the main thread,
     * rejecting anything larger than [MAX_BACKUP_BYTES].
     */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        queryDeclaredSize(uri)?.let { ensureWithinLimit(it) }
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw InvalidBackupException("Could not open the selected backup file")
            stream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                val out = ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    ensureWithinLimit(out.size().toLong())
                }
                out.toByteArray().toString(Charsets.UTF_8)
            }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: java.io.IOException) {
            throw InvalidBackupException("Could not read the backup file: ${e.message}", e)
        } catch (e: SecurityException) {
            throw InvalidBackupException("No permission to read the selected file: ${e.message}", e)
        }
    }

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

    companion object {
        const val MAX_BACKUP_BYTES = 50L * 1024 * 1024 // 50 MiB
        private const val BUFFER_SIZE = 64 * 1024
    }
}
