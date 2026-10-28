package com.walkory.app.platform.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.walkory.app.data.local.*
import com.walkory.app.domain.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ReportGenerationTest {
    private val fixture = PersistenceFixture()
    @After fun close() { fixture.close() }
    private fun report(id: InspectionId, locale: String = "en", quality: PdfPhotoQuality = PdfPhotoQuality.BALANCED) = ReportRequest(id, locale, quality)
    private fun text(file: File): String = PdfDocument(PdfReader(file)).use { pdf ->
        (1..pdf.numberOfPages).joinToString("\n") { PdfTextExtractor.getTextFromPage(pdf.getPage(it)) }
    }
    private fun clean(value: String) = value.replace(Regex("\\s+"), "")
    private fun image(photo: Photograph, width: Int = 360, height: Int = 180) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply { color = Color.RED }
            canvas.drawRect(0f, 0f, width / 2f, height / 2f, paint)
            paint.color = Color.BLUE
            canvas.drawRect(width / 2f, height / 2f, width.toFloat(), height.toFloat(), paint)
            fixture.files.resolve(photo.fileId).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)) }
        } finally { bitmap.recycle() }
    }
    private fun sample(many: Boolean = false): Inspection {
        val condition = PersistenceRecords.photo("Normal evidence — исправное состояние")
        val photos = List(if (many) 14 else 1) { PersistenceRecords.photo("Detail ${it + 1} — подпись") }
        val checks = listOf(
            InspectionCheck(CheckId.new(), "Normal floor — исправный пол", "Surface guidance", CheckState.OK, "Normal comment", listOf(condition)),
            InspectionCheck(CheckId.new(), "Window unchecked", state = CheckState.UNCHECKED),
            InspectionCheck(CheckId.new(), "Heating not applicable", state = CheckState.NOT_APPLICABLE),
            InspectionCheck(CheckId.new(), "Door issues", state = CheckState.HAS_ISSUE,
                comment = if (many) ("Длинный комментарий с переносами. Long English paragraph. ".repeat(130) + "BEGINWORD" + "Ж".repeat(700) + "ENDWORD") else "General door comment",
                issues = listOf(Issue(IssueId.new(), "Individual scratch — отдельная царапина", photos), Issue(IssueId.new(), "Second separate issue"))),
            InspectionCheck(CheckId.new(), "Explicit problematic point", state = CheckState.HAS_ISSUE),
        )
        val inspection = InspectionOperations().create("Authored title — Осмотр", "Sample Street 21 — Квартира", InspectionRole.TENANT,
            LocalDate.of(2026, 2, 3), "Sample Inspector").accepted().copy(rooms = listOf(
            InspectionRoom(RoomId.new(), RoomType.KITCHEN, "First kitchen — Кухня", checks),
            InspectionRoom(RoomId.new(), RoomType.CUSTOM, "Second empty room", emptyList()),
        ))
        image(condition)
        photos.forEach { image(it) }
        return inspection
    }

    @Test fun completeLongBilingualReportPreservesEverySectionAndRendersEveryPage() = runBlocking {
        val inspection = sample(many = true)
        fixture.inspections.create(inspection).accepted()
        val sourceBytes = fixture.files.resolve(inspection.rooms[0].checks[0].conditionPhotos[0].fileId).readBytes()
        var offMain = true
        val generated = fixture.reports.generate(report(inspection.id, "ru")) { offMain = offMain && Looper.myLooper() != Looper.getMainLooper() }.accepted()
        assertTrue(offMain)
        val file = fixture.reportFiles.report(generated.fileId)
        PdfDocument(PdfReader(file)).use { pdf ->
            assertEquals(generated.pageCount, pdf.numberOfPages)
            assertTrue(pdf.numberOfPages > 1)
            val all = clean(text(file))
            listOf("Отчёт об осмотре недвижимости", "Осмотр: Authored title — Осмотр", "Sample Street 21 — Квартира", "3 февраля 2026",
                "Sample Inspector", "арендатор при заселении", "Статус: в процессе", "Проверено: 4 / 5", "Не проверено: 1",
                "Пунктов с замечаниями: 2", "Отдельных замечаний: 2", "Surface guidance", "Normal comment", "Normal evidence — исправное состояние",
                "не проверено", "в порядке", "не применимо", "есть замечание", "Individual scratch — отдельная царапина", "Second separate issue",
                "BEGINWORD", "ENDWORD", "Отдельные замечания не добавлены.", "Second empty room", "нет пунктов проверки", "SIL Open Font License 1.1").forEach {
                assertTrue("Missing content: $it", all.contains(clean(it)))
            }
            inspection.rooms[0].checks.forEach { assertTrue(all.contains(clean(it.title))) }
            repeat(14) { assertTrue(all.contains(clean("Detail ${it + 1} — подпись"))) }
            assertTrue(all.indexOf(clean("First kitchen")) < all.indexOf(clean("Second empty room")))
            assertTrue(pdf.documentInfo.producer.contains("iText", ignoreCase = true))
            assertEquals("Walkory", pdf.documentInfo.creator)
            assertTrue(pdf.documentInfo.subject.contains("AGPLv3"))
            for (index in 1..pdf.numberOfPages) {
                val page = pdf.getPage(index)
                assertEquals(595f, page.pageSize.width, 1f)
                assertEquals(842f, page.pageSize.height, 1f)
                assertTrue(clean(PdfTextExtractor.getTextFromPage(page)).contains(clean("Страница $index")))
                val fonts = page.resources.pdfObject.getAsDictionary(PdfName.Font)
                for (key in fonts.keySet()) {
                    val descriptor = fonts.getAsDictionary(key).getAsArray(PdfName.DescendantFonts)?.getAsDictionary(0)?.getAsDictionary(PdfName.FontDescriptor)
                    assertNotNull(descriptor?.getAsStream(PdfName.FontFile2))
                }
            }
        }
        render(file, generated.pageCount)
        assertEquals(inspection, fixture.inspections.read(inspection.id))
        assertArrayEquals(sourceBytes, fixture.files.resolve(inspection.rooms[0].checks[0].conditionPhotos[0].fileId).readBytes())
        assertTrue(File(fixture.directory, "staging").listFiles()!!.isEmpty())
    }

    @Test fun concurrentEditsAndOriginalDeletionOnlyChangeTheNextGeneration() = runBlocking {
        val inspection = sample()
        fixture.inspections.create(inspection).accepted()
        val captured = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val generation = async(Dispatchers.Default) {
            fixture.reports.generate(report(inspection.id)) { if (it == ReportProgress.Rendering(0, 5)) { captured.complete(Unit); resume.await() } }.accepted()
        }
        withTimeout(10_000) { captured.await() }
        val photo = inspection.rooms[0].checks[0].conditionPhotos[0]
        val staged = File(fixture.directory, "staging").listFiles()!!.single()
        assertTrue(staged.listFiles()!!.any { it.readBytes().contentEquals(fixture.files.resolve(photo.fileId).readBytes()) })
        fixture.inspections.update(inspection.id) { old ->
            DomainResult.Success(old.copy(details = old.details.copy(name = "Next version only"), rooms = old.rooms.map { room ->
                room.copy(checks = room.checks.map { it.copy(conditionPhotos = emptyList()) })
            }))
        }.accepted()
        assertFalse(fixture.files.resolve(photo.fileId).exists())
        resume.complete(Unit)
        val first = generation.await()
        val firstText = text(fixture.reportFiles.report(first.fileId))
        assertTrue(firstText.contains("Authored title"))
        assertTrue(clean(firstText).contains(clean("Normal evidence — исправное состояние")))
        assertFalse(firstText.contains("Next version only"))
        val next = fixture.reports.generate(report(inspection.id)).accepted()
        val nextText = text(fixture.reportFiles.report(next.fileId))
        assertTrue(nextText.contains("Next version only"))
        assertFalse(nextText.contains("Normal evidence"))
        assertTrue(fixture.reportFiles.report(first.fileId).exists())
        assertTrue(File(fixture.directory, "staging").listFiles()!!.isEmpty())
    }

    @Test fun cancellationAtCaptureLayoutAndFinalizationRemovesOnlyTemporaryWork() = runBlocking {
        val inspection = sample()
        fixture.inspections.create(inspection).accepted()
        val existing = fixture.reports.generate(report(inspection.id)).accepted()
        for (phase in listOf<ReportProgress>(ReportProgress.Staging, ReportProgress.Rendering(0, 5), ReportProgress.Finalizing)) {
            val reached = CompletableDeferred<Unit>()
            val generation = launch(Dispatchers.Default) {
                fixture.reports.generate(report(inspection.id)) { if (it == phase) { reached.complete(Unit); awaitCancellation() } }
            }
            withTimeout(15_000) { reached.await() }
            generation.cancelAndJoin()
            assertTrue(generation.isCancelled)
            assertTrue(File(fixture.directory, "staging").listFiles()!!.isEmpty())
            assertEquals(listOf(fixture.reportFiles.report(existing.fileId).name), File(fixture.directory, "reports").listFiles()!!.map { it.name })
            assertEquals(inspection, fixture.inspections.read(inspection.id))
        }
    }

    @Test fun missingInvalidPhotosAndUnwritableOutputNeverProduceAnIncompleteReport() = runBlocking {
        val inspection = sample()
        fixture.inspections.create(inspection).accepted()
        val photo = inspection.rooms[0].checks[0].conditionPhotos[0]
        val original = fixture.files.resolve(photo.fileId).readBytes()
        assertTrue(fixture.files.resolve(photo.fileId).delete())
        assertEquals(DomainResult.Failure(DomainError.InvalidImage), fixture.reports.generate(report(inspection.id)))
        fixture.files.resolve(photo.fileId).writeText("not an image")
        assertEquals(DomainResult.Failure(DomainError.InvalidImage), fixture.reports.generate(report(inspection.id)))
        fixture.files.resolve(photo.fileId).writeBytes(original)
        val blocked = File(fixture.directory, "blocked-output").apply { writeText("Existing unrelated file") }
        val engine = ITextReportEngine(fixture.context, fixture.reportSnapshots, ReportFiles(File(fixture.directory, "other-staging"), blocked))
        assertEquals(DomainResult.Failure(DomainError.StorageUnavailable), engine.generate(report(inspection.id)))
        assertEquals("Existing unrelated file", blocked.readText())
        assertEquals(inspection, fixture.inspections.read(inspection.id))
        assertArrayEquals(original, fixture.files.resolve(photo.fileId).readBytes())
        assertTrue(File(fixture.directory, "reports").listFiles()!!.isEmpty())
        assertTrue(File(fixture.directory, "staging").listFiles()!!.isEmpty())
    }

    @Test fun optionalEmptyCompletedLandlordReportAndRestartRecoveryPreserveCompletedOutputs() = runBlocking {
        val inspection = InspectionOperations().create("Empty inspection", "Sample Street 6", InspectionRole.LANDLORD).accepted()
            .copy(state = InspectionState.COMPLETED)
        fixture.inspections.create(inspection).accepted()
        val generated = fixture.reports.generate(report(inspection.id)).accepted()
        val all = text(fixture.reportFiles.report(generated.fileId))
        listOf("landlord after move-out", "Status: completed", "Checked: 0 / 0", "No rooms recorded.").forEach { assertTrue(all.contains(it)) }
        assertFalse(all.contains("Inspector:"))
        val abandoned = File(fixture.directory, "staging/${LocalFileId.new().value}").apply { mkdirs() }
        File(abandoned, "photo").writeText("abandoned owned copy")
        val unrelated = File(fixture.directory, "staging/unrelated").apply { mkdirs() }
        val partial = File(fixture.directory, "reports/${LocalFileId.new().value}.partial").apply { writeText("incomplete") }
        val restarted = ReportFiles(File(fixture.directory, "staging"), File(fixture.directory, "reports"))
        restarted.prepare()
        assertFalse(abandoned.exists()); assertFalse(partial.exists()); assertTrue(unrelated.exists())
        assertTrue(fixture.reportFiles.report(generated.fileId).exists())
        assertEquals(inspection, fixture.inspections.read(inspection.id))
    }

    @Test fun everyPhotoPresetHonorsExifAspectRatioAndOriginalBytesWithoutUpscaling() = runBlocking {
        val photo = PersistenceRecords.photo()
        image(photo, 3200, 1600)
        val file = fixture.files.resolve(photo.fileId)
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val original = file.readBytes()
        val encoder = ReportPhotoEncoder()
        for ((preset, edge) in listOf(PdfPhotoQuality.COMPACT to 1280, PdfPhotoQuality.BALANCED to 1920, PdfPhotoQuality.HIGH to 2560)) {
            val bytes = encoder.encode(file, preset)
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertEquals(edge, decoded.height)
                assertEquals(edge / 2, decoded.width)
                // A clockwise rotation moves original top-left red to top-right.
                assertTrue(Color.red(decoded.getPixel(decoded.width * 3 / 4, decoded.height / 4)) > 200)
                assertTrue(Color.blue(decoded.getPixel(decoded.width / 4, decoded.height * 3 / 4)) > 200)
            } finally { decoded.recycle() }
            assertArrayEquals(original, file.readBytes())
        }
        val small = PersistenceRecords.photo(); image(small, 90, 45)
        val bytes = encoder.encode(fixture.files.resolve(small.fileId), PdfPhotoQuality.HIGH)
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try { assertEquals(90, decoded.width); assertEquals(45, decoded.height) } finally { decoded.recycle() }
    }

    @Test fun longMetadataIssueAndCaptionTextRemainInsidePrintablePageBounds() = runBlocking<Unit> {
        val original = sample()
        val long = "Длинный текст с переносами. Long text wraps across pages. ".repeat(100)
        val inspection = original.copy(details = original.details.copy(name = "NAMESTART $long NAMEEND",
            property = PropertySnapshot("ADDRESSSTART $long ADDRESSEND"), inspector = "INSPECTORSTART $long INSPECTOREND"),
            rooms = original.rooms.map { room -> room.copy(name = "ROOMSTART $long ROOMEND", checks = room.checks.map { check ->
                check.copy(title = "TITLESTART $long TITLEEND", guidance = "GUIDANCESTART $long GUIDANCEEND",
                    conditionPhotos = check.conditionPhotos.map { it.copy(caption = "CAPTIONSTART $long CAPTIONEND") },
                    issues = check.issues.map { it.copy(description = "ISSUESTART $long ISSUEEND") })
            }) })
        fixture.inspections.create(inspection).accepted()
        val generated = fixture.reports.generate(report(inspection.id)).accepted()
        val file = fixture.reportFiles.report(generated.fileId)
        val all = clean(text(file))
        for (kind in listOf("NAME", "ADDRESS", "INSPECTOR", "ROOM", "TITLE", "GUIDANCE", "CAPTION", "ISSUE")) {
            assertTrue("Missing $kind start", all.contains("${kind}START"))
            assertTrue("Missing $kind end", all.contains("${kind}END"))
        }
        PdfDocument(PdfReader(file)).use { pdf ->
            for (index in 1..pdf.numberOfPages) {
                val page = pdf.getPage(index)
                val listener = object : com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener {
                    override fun getSupportedEvents() = setOf(com.itextpdf.kernel.pdf.canvas.parser.EventType.RENDER_TEXT)
                    override fun eventOccurred(data: com.itextpdf.kernel.pdf.canvas.parser.data.IEventData,
                        type: com.itextpdf.kernel.pdf.canvas.parser.EventType) {
                        val info = data as com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
                        val bounds = info.ascentLine.boundingRectangle
                        assertTrue("Text outside left print area on page $index", bounds.x >= 20f)
                        assertTrue("Text outside right print area on page $index", bounds.right <= page.pageSize.width - 20f)
                        assertTrue("Text below print area on page $index", bounds.y >= 10f)
                        assertTrue("Text above print area on page $index", bounds.top <= page.pageSize.height - 20f)
                    }
                }
                com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor(listener).processPageContent(page)
            }
        }
        val evidence = File(fixture.context.cacheDir, "report-evidence").apply { mkdirs() }
        file.copyTo(File(evidence, "long-fields-report.pdf"), overwrite = true)
    }

    @Test fun mirroredExifOrientationsAndTransparencyHaveCorrectVisiblePixels() = runBlocking {
        val photo = PersistenceRecords.photo()
        image(photo, 360, 180)
        val source = fixture.files.resolve(photo.fileId)
        // Red quadrant's normalized location after each EXIF transform.
        val redCorners = listOf(1 to (1 to 1), 2 to (3 to 1), 3 to (3 to 3), 4 to (1 to 3),
            5 to (1 to 1), 6 to (3 to 1), 7 to (3 to 3), 8 to (1 to 3))
        for ((orientation, red) in redCorners) {
            ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
            val bytes = ReportPhotoEncoder().encode(source, PdfPhotoQuality.COMPACT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertEquals(if (orientation >= 5) 180 else 360, bitmap.width)
                assertEquals(if (orientation >= 5) 360 else 180, bitmap.height)
                val pixel = bitmap.getPixel(bitmap.width * red.first / 4, bitmap.height * red.second / 4)
                assertTrue("Wrong orientation $orientation", Color.red(pixel) > 200 && Color.blue(pixel) < 50)
            } finally { bitmap.recycle() }
        }
        val transparent = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            transparent.eraseColor(Color.TRANSPARENT)
            source.outputStream().use { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { transparent.recycle() }
        val bytes = ReportPhotoEncoder().encode(source, PdfPhotoQuality.HIGH)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(80, bitmap.width); assertEquals(40, bitmap.height)
            val center = bitmap.getPixel(40, 20)
            assertTrue(Color.red(center) > 245 && Color.green(center) > 245 && Color.blue(center) > 245)
        } finally { bitmap.recycle() }
    }

    private fun render(file: File, expectedPages: Int) {
        val evidence = File(fixture.context.cacheDir, "report-evidence").apply { mkdirs() }
        file.copyTo(File(evidence, "complete-report.pdf"), overwrite = true)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(expectedPages, renderer.pageCount)
                for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        File(evidence, "page-${index + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
}
