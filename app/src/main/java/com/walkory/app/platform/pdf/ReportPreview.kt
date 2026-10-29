package com.walkory.app.platform.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** No page cache. Rapid paging cannot overlap native render/bitmap allocations. */
internal object ReportPreview {
    const val MAX_EDGE = 2048
    private val rendering = Mutex()

    suspend fun render(file: File, index: Int, requestedWidth: Int, publish: (Bitmap, Int) -> Unit) = rendering.withLock {
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use { page ->
                        val scale = minOf(requestedWidth.coerceIn(1, MAX_EDGE).toFloat() / page.width,
                            MAX_EDGE.toFloat() / maxOf(page.width, page.height))
                        val bitmap = createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
                            (page.height * scale).roundToInt().coerceAtLeast(1))
                        var published = false
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            currentCoroutineContext().ensureActive()
                            publish(bitmap, renderer.pageCount)
                            published = true
                        } finally {
                            if (!published) bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}
