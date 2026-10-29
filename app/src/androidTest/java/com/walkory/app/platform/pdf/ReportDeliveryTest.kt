package com.walkory.app.platform.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.walkory.app.data.local.PersistenceFixture
import com.walkory.app.data.local.read
import com.walkory.app.data.local.accepted
import com.walkory.app.domain.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ReportDeliveryTest {
    private val fixture = PersistenceFixture()
    private val output = File(fixture.context.cacheDir, "reports/delivery-test-${UUID.randomUUID()}")
    private val files = ReportFiles(File(fixture.directory, "delivery-staging"), output)
    private val engine = ITextReportEngine(fixture.context, fixture.reportSnapshots, files)
    private val delivery = AndroidReportDelivery(fixture.context, files)
    @After fun close() { output.deleteRecursively(); fixture.close() }

    private fun inspection(): Inspection = InspectionOperations().create("Rental review", "Sample Street 8", InspectionRole.TENANT).accepted().copy(
        rooms = listOf(InspectionRoom(RoomId.new(), RoomType.KITCHEN, "Kitchen", List(65) {
            InspectionCheck(CheckId.new(), "Check ${it + 1}", state = CheckState.OK,
                comment = "Recorded condition — Состояние помещения. ".repeat(14))
        })))

    @Test fun completeArtifactIsReadOnlyAndRegenerationPreservesEarlierBytes() = runBlocking {
        val original = inspection()
        fixture.inspections.create(original).accepted()
        val first = engine.generate(ReportRequest(original.id, "en", PdfPhotoQuality.BALANCED)).accepted()
        val firstBytes = delivery.file(first.fileId).readBytes()
        val send = delivery.share(first.fileId)
        assertEquals("application/pdf", send.type)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags)
        val uri = androidx.core.content.IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertArrayEquals(firstBytes, fixture.context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        try {
            fixture.context.contentResolver.openFileDescriptor(uri, "rw")?.close()
            fail("Writable report URI")
        } catch (_: SecurityException) { }
        val updated = original.copy(details = original.details.copy(name = "Updated rental review"))
        fixture.inspections.update(original.id) { DomainResult.Success(updated) }.accepted()
        val second = engine.generate(ReportRequest(original.id, "en", PdfPhotoQuality.BALANCED)).accepted()
        assertNotEquals(first.fileId, second.fileId)
        assertArrayEquals(firstBytes, delivery.file(first.fileId).readBytes())
        PdfDocument(PdfReader(delivery.file(second.fileId))).use { pdf ->
            assertTrue(PdfTextExtractor.getTextFromPage(pdf.getPage(1)).contains("Updated rental review"))
        }
        assertEquals(updated, fixture.inspections.read(original.id))
        try {
            delivery.save(second.fileId, "content://unavailable.documents/new.pdf") { _, _ -> }
            fail("Missing provider accepted")
        } catch (_: java.io.FileNotFoundException) { }
        assertArrayEquals(firstBytes, delivery.file(first.fileId).readBytes())
        assertEquals(updated, fixture.inspections.read(original.id))
    }

    @Test fun longReportRendersEveryPageWithBoundedBitmapsAndNoLeakedDescriptors() = runBlocking {
        val original = inspection()
        fixture.inspections.create(original).accepted()
        val report = engine.generate(ReportRequest(original.id, "ru", PdfPhotoQuality.BALANCED)).accepted()
        assertTrue(report.pageCount >= 20)
        val descriptors = File("/proc/self/fd").list()!!.size
        repeat(2) {
            for (index in 0 until report.pageCount) {
                ReportPreview.render(delivery.file(report.fileId), index, 10_000) { bitmap, count ->
                    try {
                        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                        assertEquals(report.pageCount, count)
                        assertTrue(bitmap.width <= ReportPreview.MAX_EDGE && bitmap.height <= ReportPreview.MAX_EDGE)
                        assertTrue(bitmap.allocationByteCount <= ReportPreview.MAX_EDGE * ReportPreview.MAX_EDGE * 4)
                        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
                    } finally { bitmap.recycle() }
                }
            }
        }
        assertTrue("Leaked descriptors", File("/proc/self/fd").list()!!.size <= descriptors + 4)
        assertEquals(original, fixture.inspections.read(original.id))
        try {
            ReportPreview.render(delivery.file(report.fileId), report.pageCount, 800) { _, _ -> fail("Invalid page published") }
            fail("Invalid page accepted")
        } catch (_: IllegalArgumentException) { }
        ReportPreview.render(delivery.file(report.fileId), 0, 800) { bitmap, _ -> bitmap.recycle() }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun cancellingAnActualProviderCopyPreservesTheEarlierExportAndInspection() = runBlocking {
        val original = inspection()
        fixture.inspections.create(original).accepted()
        val report = engine.generate(ReportRequest(original.id, "en", PdfPhotoQuality.BALANCED)).accepted()
        val bytes = delivery.file(report.fileId).readBytes()
        val resolver = fixture.context.contentResolver
        val created = mutableListOf<Uri>()
        fun destination(): Uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Rental-review-${UUID.randomUUID()}.pdf")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            })).also { created += it }
        try {
            val earlier = destination()
            delivery.save(report.fileId, earlier.toString()) { _, _ -> }
            assertArrayEquals(bytes, resolver.openInputStream(earlier)!!.use { it.readBytes() })
            val next = destination()
            var copied = 0L
            val copying = launch {
                val owner = currentCoroutineContext().job
                delivery.save(report.fileId, next.toString()) { written, _ ->
                    copied = written
                    owner.cancel()
                }
            }
            copying.join()
            assertTrue(copying.isCancelled)
            assertTrue(copied > 0 && copied < bytes.size)
            assertArrayEquals(bytes, resolver.openInputStream(earlier)!!.use { it.readBytes() })
            assertArrayEquals(bytes, delivery.file(report.fileId).readBytes())
            assertEquals(original, fixture.inspections.read(original.id))
        } finally { created.forEach { resolver.delete(it, null, null) } }
    }
    @Test fun rapidPagingQueuesOneRenderAndCancelledRequestsNeverPublishBitmaps() = runBlocking {
        val original = inspection()
        fixture.inspections.create(original).accepted()
        val report = engine.generate(ReportRequest(original.id, "en", PdfPhotoQuality.BALANCED)).accepted()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val publications = AtomicInteger()
        val first = launch(Dispatchers.IO) {
            ReportPreview.render(delivery.file(report.fileId), 0, 800) { bitmap, _ ->
                try { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                finally { bitmap.recycle() }
            }
        }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val queued = List(12) { index ->
                launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    ReportPreview.render(delivery.file(report.fileId), index + 1, 800) { bitmap, _ ->
                        publications.incrementAndGet()
                        bitmap.recycle()
                    }
                }
            }
            assertEquals(0, publications.get())
            queued.forEach { it.cancel() }
            release.countDown()
            queued.forEach { it.join() }
            first.join()
            assertEquals(0, publications.get())
        } finally { release.countDown(); first.cancel() }
    }
}
