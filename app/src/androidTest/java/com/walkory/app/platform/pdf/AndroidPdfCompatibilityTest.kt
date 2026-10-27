package com.walkory.app.platform.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPdfCompatibilityTest {
    @Test
    fun unicodeTextSurvivesPaginationWithAnEmbeddedFontAndAndroidRendering() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "pdf-compatibility")
        check(directory.isDirectory || directory.mkdirs())
        val output = File(directory, "unicode-pagination.pdf")
        val fontBytes = instrumentation.context.assets.open("fonts/NotoSans-Regular.ttf").use {
            it.readBytes()
        }
        val font = PdfFontFactory.createFont(
            fontBytes,
            PdfEncodings.IDENTITY_H,
            PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED,
        )
        Document(PdfDocument(PdfWriter(output)), PageSize.A4).use { document ->
            document.setFont(font).setFontSize(12f)
            document.setMargins(36f, 36f, 36f, 36f)
            document.add(Paragraph("Начало осмотра — Property inspection"))
            repeat(90) { index ->
                document.add(
                    Paragraph(
                        "Пункт ${index + 1}. Стены, потолок и пол: состояние зафиксировано. " +
                            "Windows, doors and surfaces are recorded independently.",
                    ),
                )
            }
            document.add(Paragraph("Конец осмотра — End of inspection"))
        }
        val pageCount = PdfDocument(PdfReader(output)).use { pdf ->
            assertTrue("Flowing text must paginate rather than be clipped", pdf.numberOfPages > 1)
            val text = buildString {
                for (page in 1..pdf.numberOfPages) {
                    append(PdfTextExtractor.getTextFromPage(pdf.getPage(page)))
                    append('\n')
                }
            }
            assertTrue(text.contains("Начало осмотра"))
            assertTrue(text.contains("Property inspection"))
            repeat(90) { index ->
                assertTrue("Missing paragraph ${index + 1}", text.contains("Пункт ${index + 1}."))
            }
            assertTrue(text.contains("Конец осмотра"))
            assertTrue(text.contains("End of inspection"))
            assertTrue(pdf.documentInfo.producer.lowercase(Locale.ROOT).contains("itext"))
            val fonts = pdf.getPage(1).pdfObject
                .getAsDictionary(PdfName.Resources).getAsDictionary(PdfName.Font)
            assertNotNull("Unicode text requires a font resource", fonts)
            for (name in fonts.keySet()) {
                val descriptor = fonts.getAsDictionary(name)
                    .getAsArray(PdfName.DescendantFonts)
                    ?.getAsDictionary(0)
                    ?.getAsDictionary(PdfName.FontDescriptor)
                assertNotNull("Font outlines must be embedded", descriptor?.getAsStream(PdfName.FontFile2))
            }
            pdf.numberOfPages
        }
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals("Android must see every generated page", pageCount, renderer.pageCount)
                renderPage(renderer, 0, File(directory, "first-page.png"))
                renderPage(renderer, pageCount - 1, File(directory, "last-page.png"))
            }
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, output: File) {
        renderer.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                output.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
