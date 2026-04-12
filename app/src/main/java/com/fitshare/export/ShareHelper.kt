package com.fitshare.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareHelper {

    /**
     * Saves bitmap to MediaStore (visible in gallery) and returns its Uri.
     */
    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? =
        withContext(Dispatchers.IO) {
            val filename = "fitshare_${System.currentTimeMillis()}.png"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FitShare")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }

    /**
     * Saves bitmap to cache dir and returns a shareable FileProvider Uri.
     */
    suspend fun saveBitmapForSharing(context: Context, bitmap: Bitmap): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "stories").also { it.mkdirs() }
                val file = File(cacheDir, "story_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Opens the Android share sheet for an image Uri.
     */
    fun shareImage(context: Context, uri: Uri, activityLabel: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Mein $activityLabel Workout via FitShare")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Story teilen via")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
