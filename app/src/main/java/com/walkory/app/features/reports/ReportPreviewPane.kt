package com.walkory.app.features.reports

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.GeneratedReport
import com.walkory.app.platform.pdf.ReportPreview
import com.walkory.app.ui_kit.WalkorySpacing
import java.io.File
import kotlinx.coroutines.CancellationException

@Composable
internal fun ReportPreviewPane(report: GeneratedReport, file: File, modifier: Modifier = Modifier) {
    var zoom by rememberSaveable(report.fileId.value) { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { _, scale, _, _ -> zoom = (zoom * scale).coerceIn(1f, 5f) }
    val list = rememberLazyListState()
    val horizontal = rememberScrollState()
    BoxWithConstraints(modifier.clipToBounds().transformable(transform, canPan = { false }).testTag("report_preview")) {
        val pageWidth = maxWidth * zoom
        Row(Modifier.fillMaxSize().horizontalScroll(horizontal)) {
            LazyColumn(Modifier.width(pageWidth).fillMaxHeight().testTag("report_pages"), state = list,
                contentPadding = PaddingValues(vertical = WalkorySpacing.small),
                verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                items(report.pageCount, key = { it }) { page ->
                    PdfPage(file, page, report.pageCount)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(file: File, page: Int, count: Int) {
    var bitmap by remember(file, page) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file, page) { mutableStateOf(false) }
    var attempt by remember(file, page) { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(595f / 842f).testTag("report_page_$page"), contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceAtMost(ReportPreview.MAX_EDGE)
        LaunchedEffect(file, page, width, attempt) {
            error = false
            try {
                ReportPreview.render(file, page, width) { rendered, _ -> bitmap = rendered }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
        // Lazy items own only their rendered page; leaving composition releases the bitmap reference.
        DisposableEffect(file, page) { onDispose { bitmap = null } }
        bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.reports_position, page + 1, count),
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (error) Column(Modifier.padding(WalkorySpacing.medium), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.reports_preview_error), color = MaterialTheme.colorScheme.error)
            TextButton({ attempt++ }, Modifier.testTag("report_preview_retry_$page")) { Text(stringResource(R.string.reports_retry)) }
        } else if (bitmap == null) CircularProgressIndicator()
    }
}
