package com.fitshare.export

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Video backgrounds: the editor works on the first frame, the export re-encodes the whole clip. */
object VideoExporter {
    /** Picked videos are copied into app storage with this name ending (photos without). */
    const val VIDEO_SUFFIX = "-video"

    fun isVideo(uri: Uri?): Boolean = uri?.path?.endsWith(VIDEO_SUFFIX) == true

    /** First frame, upright, at most story size. */
    fun firstFrame(context: Context, uri: Uri): Bitmap? = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, StoryRenderer.W.toInt(), StoryRenderer.H.toInt())
            } else r.getFrameAtTime(0)
            // the renderer draws on a software canvas and reads pixels for the matching backdrop
            frame?.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false).also { _ -> it.recycle() } }
        } finally {
            r.release()
        }
    } catch (e: Exception) { null }

    /**
     * Writes a 1080×1920 MP4, silent unless [keepSound]: the video cropped to fill ([crop]) or fitted, with [overlay]
     * (story-sized, transparent where the video shows) on top. HDR is tone-mapped to SDR, which Instagram shows right.
     */
    @OptIn(UnstableApi::class)
    suspend fun export(context: Context, video: Uri, overlay: Bitmap, crop: Boolean, keepSound: Boolean, out: File, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.Main) { // Transformer wants a Looper thread
            suspendCancellableCoroutine { cont ->
                val layout = if (crop) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
                val item = EditedMediaItem.Builder(MediaItem.fromUri(video))
                    .setRemoveAudio(!keepSound)
                    .setEffects(Effects(emptyList(), listOf(
                        Presentation.createForWidthAndHeight(StoryRenderer.W.toInt(), StoryRenderer.H.toInt(), layout),
                        OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(overlay)))
                    )))
                    .build()
                val composition = Composition.Builder(EditedMediaItemSequence(item))
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(out)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            out.delete()
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer.start(composition, out.path)

                val main = Handler(Looper.getMainLooper())
                val holder = ProgressHolder()
                main.post(object : Runnable {
                    override fun run() {
                        if (!cont.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                        main.postDelayed(this, 300)
                    }
                })
                cont.invokeOnCancellation { main.post { transformer.cancel(); out.delete() } }
            }
        }
}
