package com.babytracker.ui.milestone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PHOTO_DIR = "milestone_photos"
private const val PHOTO_PREFIX = "moment"

/** Longest-edge decode target for the 72dp timeline thumbnails. */
const val MILESTONE_THUMBNAIL_TARGET_PX = 256

/** Longest-edge decode target for the full-width detail hero (≈ phone width). */
const val MILESTONE_HERO_TARGET_PX = 1080

/**
 * Process-wide cache of decoded moment bitmaps, keyed by `uri@targetPx`. Scrolling the
 * timeline disposes off-screen items, so without this every re-entry would re-read and
 * re-decode the file from disk on the next frame.
 *
 * Sized by bytes, not entry count: the cache mixes ~256px timeline thumbnails (~0.25 MB) with
 * ~1080px detail heroes (several MB each), so a fixed entry count could pin tens of MB of large
 * heroes. The [LruCache.sizeOf] override evicts by real footprint to hold the budget whatever the mix.
 */
private const val BITMAP_CACHE_BYTES = 24 * 1024 * 1024 // 24 MB
private val bitmapCache = object : LruCache<String, ImageBitmap>(BITMAP_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
}

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

/**
 * Loads a downsampled [ImageBitmap] for [uri] off the main thread, or null. [targetPx] is the
 * longest-edge decode target: pass [MILESTONE_THUMBNAIL_TARGET_PX] for list thumbnails and
 * [MILESTONE_HERO_TARGET_PX] for the full-width detail hero so it isn't over-downsampled.
 * Decoded results are memoized in [bitmapCache], so a cache hit paints on the first frame.
 */
@Composable
fun rememberMilestoneBitmap(uri: String?, targetPx: Int = MILESTONE_THUMBNAIL_TARGET_PX): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = uri?.let { "$it@$targetPx" }
    var bitmap by remember(cacheKey) { mutableStateOf(cacheKey?.let { bitmapCache.get(it) }) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null && uri != null && cacheKey != null) {
            loadDownsampledBitmap(context, uri, targetPx)?.let {
                bitmapCache.put(cacheKey, it)
                bitmap = it
            }
        }
    }
    return bitmap
}

private suspend fun loadDownsampledBitmap(context: Context, uri: String, targetPx: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
            }
            val decoded = context.contentResolver.openInputStream(parsed)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@runCatching null
            // BitmapFactory ignores the EXIF orientation tag, so cameras that store
            // landscape sensor pixels with a rotation tag would render sideways. Apply
            // the tag here so the photo shows in the orientation the user shot it.
            applyExifOrientation(context, parsed, decoded).asImageBitmap()
        }.getOrNull()
    }

/** Reads the EXIF orientation tag for [uri], defaulting to normal on any failure. */
private fun readExifOrientation(context: Context, uri: Uri): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/** Rotates/flips [bitmap] to match the source photo's EXIF orientation. */
private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val matrix = Matrix()
    when (readExifOrientation(context, uri)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    var sample = 1
    var largest = maxOf(width, height)
    while (largest / 2 >= targetPx) {
        largest /= 2
        sample *= 2
    }
    return sample
}
