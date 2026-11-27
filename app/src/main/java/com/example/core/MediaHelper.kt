package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object MediaHelper {

    fun createCameraCaptureUri(context: Context): Uri {
        val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val imageFile = File(capturesDir, "scene_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    /**
     * Decodes and downscales a URI into a memory-safe Bitmap (max edge 1024px)
     * to prevent OutOfMemoryError and keep multimodal payloads fast and well under 20MB.
     */
    fun decodeScaledBitmap(context: Context, uri: Uri, maxEdgePx: Int = 1024): Bitmap? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            while (origWidth / sampleSize > maxEdgePx * 2 || origHeight / sampleSize > maxEdgePx * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val longest = max(sampled.width, sampled.height)
            if (longest <= maxEdgePx) return sampled

            val scale = maxEdgePx.toFloat() / longest.toFloat()
            val targetW = (sampled.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (sampled.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
            if (scaled !== sampled) {
                sampled.recycle()
            }
            scaled
        }.getOrNull()
    }
}
