package com.walkory.app.platform.pdf

import android.content.Context
import android.content.res.Configuration
import android.system.ErrnoException
import android.system.OsConstants
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.walkory.app.R
import com.walkory.app.domain.*
import java.io.FileOutputStream
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class ITextReportEngine(
    private val context: Context,
    private val snapshots: ReportSnapshots,
    private val files: ReportFiles,
    private val photos: ReportPhotoEncoder = ReportPhotoEncoder(),
) : ReportEngine {
    override suspend fun generate(request: ReportRequest, onProgress: suspend (ReportProgress) -> Unit): DomainResult<GeneratedReport> {
        val id = LocalFileId.new()
        var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                files.prepare()
                onProgress(ReportProgress.Staging)
                val rendered = snapshots.withSnapshot(request.inspectionId) { snapshot ->
                    render(snapshot, request, id, onProgress)
                }
                if (rendered is DomainResult.Failure) return@withContext rendered
                currentCoroutineContext().ensureActive()
                if (!files.partial(id).renameTo(files.report(id))) throw IOException("Cannot commit PDF")
                DomainResult.Success(GeneratedReport(id, (rendered as DomainResult.Success).value))
            }
            delivered = result is DomainResult.Success
            return result
        } catch (_: UnreadableReportPhoto) {
            return DomainResult.Failure(DomainError.InvalidImage)
        } catch (failure: IOException) {
            return DomainResult.Failure(storageError(failure))
        } catch (failure: com.itextpdf.io.exceptions.IOException) {
            return DomainResult.Failure(storageError(failure))
        } catch (failure: com.itextpdf.kernel.exceptions.PdfException) {
            return DomainResult.Failure(if (hasFullStorageCause(failure)) DomainError.InsufficientStorage else DomainError.ReportGenerationFailed)
        } catch (_: SecurityException) {
            return DomainResult.Failure(DomainError.StorageUnavailable)
        } finally {
            if (!delivered) withContext(Dispatchers.IO + NonCancellable) {
                files.partial(id).delete()
                files.report(id).delete()
            }
        }
    }

    private suspend fun render(snapshot: ReportSnapshot, request: ReportRequest, id: LocalFileId,
        onProgress: suspend (ReportProgress) -> Unit): Int {
        val locale = Locale.forLanguageTag(request.localeTag)
        val labels = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
        fun text(resource: Int, vararg args: Any): String = labels.getString(resource, *args)
        val inspection = snapshot.inspection
        val progress = inspection.progress()
        val fontBytes = context.assets.open("fonts/NotoSans-Regular.ttf").use { it.readBytes() }
        val coroutine = currentCoroutineContext()
        FileOutputStream(files.partial(id)).use { stream ->
            PdfWriter(stream).use { writer ->
                PdfDocument(writer).use { pdf ->
                    val font = PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED)
                    pdf.documentInfo.setTitle(text(R.string.reports_document_title)).setCreator("Walkory")
                        .setSubject(text(R.string.reports_attribution))
                    pdf.addEventHandler(PdfDocumentEvent.END_PAGE, object : AbstractPdfDocumentEventHandler() {
                        override fun onAcceptedEvent(event: AbstractPdfDocumentEvent) {
                            coroutine.ensureActive()
                            val page = (event as PdfDocumentEvent).page
                            val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdf)
                            canvas.beginText().setFontAndSize(font, 9f).moveText(42.0, 24.0)
                                .showText(text(R.string.reports_page, pdf.getPageNumber(page))).endText()
                            canvas.release()
                        }
                    })
                    Document(pdf, PageSize.A4).use { document ->
                        document.setMargins(42f, 42f, 48f, 42f)
                        document.setFont(font).setFontSize(11f)
                        fun paragraph(value: String, size: Float = 11f, heading: Boolean = false) {
                            coroutine.ensureActive()
                            document.add(Paragraph(value).setFontSize(size).setMarginBottom(6f).setKeepWithNext(heading))
                        }
                        paragraph(text(R.string.reports_document_title), 20f, true)
                        paragraph(text(R.string.reports_name, inspection.details.name), 14f)
                        paragraph(text(R.string.reports_address, inspection.details.property.address))
                        paragraph(text(R.string.reports_date, inspection.details.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))))
                        paragraph(text(if (inspection.details.role == InspectionRole.TENANT) R.string.reports_tenant else R.string.reports_landlord))
                        inspection.details.inspector?.takeIf { it.isNotBlank() }?.let { paragraph(text(R.string.reports_inspector, it)) }
                        paragraph(text(if (inspection.state == InspectionState.COMPLETED) R.string.reports_completed else R.string.reports_in_progress))
                        paragraph(text(R.string.reports_summary), 15f, true)
                        paragraph(text(R.string.reports_rooms, inspection.rooms.size))
                        paragraph(text(R.string.reports_progress, progress.checkedChecks, progress.totalChecks))
                        paragraph(text(R.string.reports_unchecked, progress.uncheckedChecks))
                        paragraph(text(R.string.reports_problematic, progress.problematicChecks))
                        paragraph(text(R.string.reports_issue_count, progress.issueCount))
                        paragraph(text(R.string.reports_neutral_note), 10f)
                        if (inspection.rooms.isEmpty()) paragraph(text(R.string.reports_empty_rooms))
                        var completed = 0
                        onProgress(ReportProgress.Rendering(0, progress.totalChecks))
                        val stagedPhotos = files.photographs(snapshot.stagingId)
                        suspend fun photographs(values: List<Photograph>, prefix: String, label: Int, context: String) {
                            for ((index, photo) in values.withIndex()) {
                                coroutine.ensureActive()
                                // Numbered photo context remains readable when long captions flow.
                                paragraph(text(label, "$prefix.${index + 1}"), 12f, true)
                                val bytes = photos.encode(stagedPhotos.resolve(photo.fileId), request.photoQuality)
                                val image = Image(ImageDataFactory.create(bytes)).scaleToFit(PageSize.A4.width - 84f, 330f)
                                document.add(image.setMarginBottom(6f))
                                paragraph(context, 10f)
                                photo.caption?.takeIf { it.isNotEmpty() }?.let { paragraph(text(R.string.reports_caption, it), 10f) }
                                // Immediate flushing prevents the document from retaining every decoded JPEG.
                                image.xObject.flush()
                            }
                        }
                        for ((roomIndex, room) in inspection.rooms.withIndex()) {
                            paragraph(text(R.string.reports_room, roomIndex + 1, room.name), 16f, true)
                            if (room.checks.isEmpty()) paragraph(text(R.string.reports_empty_checks))
                            for ((checkIndex, check) in room.checks.withIndex()) {
                                val number = "${roomIndex + 1}.${checkIndex + 1}"
                                val checkContext = text(R.string.reports_check, number, check.title)
                                paragraph(checkContext, 13f, true)
                                paragraph(text(when (check.state) {
                                    CheckState.UNCHECKED -> R.string.reports_state_unchecked
                                    CheckState.OK -> R.string.reports_state_ok
                                    CheckState.HAS_ISSUE -> R.string.reports_state_issue
                                    CheckState.NOT_APPLICABLE -> R.string.reports_state_na
                                }))
                                check.guidance?.takeIf { it.isNotEmpty() }?.let { paragraph(text(R.string.reports_guidance, it), 10f) }
                                check.comment?.takeIf { it.isNotEmpty() }?.let { paragraph(text(R.string.reports_comment, it)) }
                                photographs(check.conditionPhotos, number, R.string.reports_condition_photo, checkContext)
                                if (check.state == CheckState.HAS_ISSUE && check.issues.isEmpty()) paragraph(text(R.string.reports_no_issues))
                                for ((issueIndex, issue) in check.issues.withIndex()) {
                                    val issueNumber = "$number.${issueIndex + 1}"
                                    paragraph(text(R.string.reports_issue, issueNumber), 12f, true)
                                    paragraph(issue.description?.takeIf { it.isNotBlank() } ?: text(R.string.reports_no_description))
                                    photographs(issue.photos, issueNumber, R.string.reports_issue_photo, text(R.string.reports_issue, issueNumber))
                                }
                                completed++
                                onProgress(ReportProgress.Rendering(completed, progress.totalChecks))
                            }
                        }
                        paragraph(text(R.string.reports_attribution), 9f)
                        onProgress(ReportProgress.Finalizing)
                        coroutine.ensureActive()
                        return pdf.numberOfPages
                    }
                }
            }
        }
    }
}

private fun hasFullStorageCause(failure: Throwable): Boolean = generateSequence(failure) { it.cause }
    .any { it is ErrnoException && (it.errno == OsConstants.ENOSPC || it.errno == OsConstants.EDQUOT) }

private fun storageError(failure: Throwable): DomainError =
    if (hasFullStorageCause(failure)) DomainError.InsufficientStorage else DomainError.StorageUnavailable
