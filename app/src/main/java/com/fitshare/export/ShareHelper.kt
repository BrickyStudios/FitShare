package com.fitshare.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareHelper {

    private fun fileName() = "fitshare_${System.currentTimeMillis()}.png"

    /** Saves to Pictures/FitShare. API < 29 needs WRITE_EXTERNAL_STORAGE granted beforehand. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName())
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FitShare")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: error("no stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FitShare")
                dir.mkdirs()
                val file = File(dir, fileName())
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/png"), null)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Writes to the app cache and returns a FileProvider Uri other apps may read. */
    suspend fun cacheForSharing(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "stories").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the latest story is ever needed
        val file = File(dir, fileName())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** An empty file for the next video export, next to the shared PNGs (only the latest story is ever needed). */
    fun newVideoFile(context: Context): File {
        val dir = File(context.cacheDir, "stories").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "fitshare_${System.currentTimeMillis()}.mp4")
    }

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Copies an exported MP4 to Movies/FitShare. API < 29 needs WRITE_EXTERNAL_STORAGE granted beforehand. */
    suspend fun saveVideoToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FitShare")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "FitShare")
                dir.mkdirs()
                val target = File(dir, file.name)
                file.copyTo(target, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("video/mp4"), null)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun share(context: Context, uri: Uri, mime: String = "image/png") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val title = if (mime.startsWith("video/")) com.fitshare.data.tr("Video teilen", "Share video") else com.fitshare.data.tr("Bild teilen", "Share image")
        context.startActivity(Intent.createChooser(intent, title))
    }
}
