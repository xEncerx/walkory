package com.walkory.app.platform.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.walkory.app.domain.PdfPhotoQuality
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class UnreadableReportPhoto(cause: Throwable? = null) : IOException("Required photograph is unreadable", cause)

/** Decodes one sampled original at a time. Only the generated JPEG is changed. */
internal class ReportPhotoEncoder {
    suspend fun encode(file: File, quality: PdfPhotoQuality): ByteArray {
        currentCoroutineContext().ensureActive()
        val (edge, jpegQuality) = when (quality) {
            PdfPhotoQuality.COMPACT -> 1280 to 70
            PdfPhotoQuality.BALANCED -> 1920 to 85
            PdfPhotoQuality.HIGH -> 2560 to 92
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadableReportPhoto()
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw UnreadableReportPhoto()
        try {
            val orientation = try {
                ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (failure: IOException) { throw UnreadableReportPhoto(failure) }
            val transform = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val rect = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            transform.mapRect(rect)
            transform.postTranslate(-rect.left, -rect.top)
            val scale = minOf(1f, edge / maxOf(rect.width(), rect.height()))
            transform.postScale(scale, scale)
            val output = createBitmap(
                (rect.width() * scale).roundToInt().coerceAtLeast(1),
                (rect.height() * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
            )
            try {
                val canvas = Canvas(output)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                currentCoroutineContext().ensureActive()
                return ByteArrayOutputStream().use { bytes ->
                    if (!output.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bytes)) throw IOException("Cannot encode report photo")
                    currentCoroutineContext().ensureActive()
                    bytes.toByteArray()
                }
            } finally { output.recycle() }
        } finally { bitmap.recycle() }
    }
}
