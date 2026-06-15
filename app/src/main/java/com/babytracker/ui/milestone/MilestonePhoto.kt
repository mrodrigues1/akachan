package com.babytracker.ui.milestone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PHOTO_DIR = "milestone_photos"
private const val PHOTO_PREFIX = "moment"
private const val THUMBNAIL_TARGET_PX = 256

/**
 * Copies the picked image into app-internal storage and returns a stable file
 * Uri. Copying (rather than persisting a content-resolver permission) guarantees
 * the photo survives across restarts and can be included in the local backup.
 * Returns null if the source could not be read.
 */
suspend fun persistMilestonePhoto(context: Context, source: Uri): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
            // Each picked photo gets a unique filename so picking a replacement never
            // overwrites the file the saved moment still points at — canceling the
            // edit leaves the previously saved photo untouched. The old file is cleaned up
            // by the ViewModel only after a successful replace/delete.
            val target = File(dir, "${PHOTO_PREFIX}_${System.currentTimeMillis()}.jpg")
            // Copy into a temp file first, verify it decodes, then atomically move it into
            // place, so a mid-copy failure never leaves a half-written photo.
            val temp = File.createTempFile("${PHOTO_PREFIX}_", ".tmp", dir)
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || !isDecodableImage(temp)) {
                temp.delete()
                return@runCatching null
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Uri.fromFile(target)
        }.getOrNull()
    }

private fun isDecodableImage(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
}

/** Loads a downsampled [ImageBitmap] for [uri] off the main thread, or null. */
@Composable
fun rememberMilestoneBitmap(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri == null) null else loadDownsampledBitmap(context, uri)
    }
    return bitmap
}

private suspend fun loadDownsampledBitmap(context: Context, uri: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(parsed)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            }
        }.getOrNull()
    }

private fun sampleSizeFor(width: Int, height: Int): Int {
    var sample = 1
    var largest = maxOf(width, height)
    while (largest / 2 >= THUMBNAIL_TARGET_PX) {
        largest /= 2
        sample *= 2
    }
    return sample
}
