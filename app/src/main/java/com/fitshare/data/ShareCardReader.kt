package com.fitshare.data

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR for share pictures (ML Kit, model bundled). The picture never leaves the phone. */
object ShareCardReader {

    suspend fun read(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // share cards are ~1080 px wide; scale down anything far bigger so OCR stays fast
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error(tr("Bild konnte nicht gelesen werden.", "Couldn't read the picture."))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener {
                        cont.resumeWithException(IllegalStateException(tr("Texterkennung fehlgeschlagen. Nimm die FIT- oder GPX-Datei.", "Text recognition failed. Use the FIT or GPX file instead."), it))
                    }
            }
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
