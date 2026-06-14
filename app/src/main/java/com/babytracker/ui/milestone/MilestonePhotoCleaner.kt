package com.babytracker.ui.milestone

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes app-owned milestone photo files. Abstracted so the ViewModel can be
 * unit-tested without Android file I/O.
 */
interface MilestonePhotoCleaner {
    suspend fun delete(photoUri: String?)
}

@Singleton
class AndroidMilestonePhotoCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) : MilestonePhotoCleaner {

    override suspend fun delete(photoUri: String?) {
        val path = photoUri?.let { Uri.parse(it) }?.path ?: return
        withContext(Dispatchers.IO) {
            val file = File(path)
            // Only ever delete files this app wrote under its private milestone photo dir.
            val photoDir = File(context.filesDir, "milestone_photos")
            if (file.parentFile?.canonicalPath == photoDir.canonicalPath && file.exists()) {
                file.delete()
            }
        }
    }
}
