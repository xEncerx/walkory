package com.walkory.app.features.reports

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.walkory.app.R
import com.walkory.app.domain.GeneratedReport
import com.walkory.app.platform.pdf.ReportPreview
import com.walkory.app.ui_kit.WalkorySpacing
import com.walkory.app.ui_kit.WalkoryIcon
import java.io.File
import kotlinx.coroutines.CancellationException

@Composable
internal fun ReportPreviewPane(report: GeneratedReport, file: File, page: Int, zoom: Float,
    onPage: (Int) -> Unit, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(report.fileId, page) { mutableStateOf<Bitmap?>(null) }
    var error by remember(report.fileId, page) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var count by remember(report.fileId) { mutableIntStateOf(report.pageCount) }
    val renderWidth = (viewport.width * 2).coerceIn(1, ReportPreview.MAX_EDGE)
    LaunchedEffect(file, page, renderWidth, attempt) {
        if (viewport.width == 0) return@LaunchedEffect
        bitmap = null
        error = false
        try {
            ReportPreview.render(file, page, renderWidth) { rendered, pages -> bitmap = rendered; count = pages }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    fun bounded(value: Offset, scale: Float): Offset {
        val image = bitmap ?: return Offset.Zero
        val fit = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        val x = ((image.width * fit * scale - viewport.width) / 2f).coerceAtLeast(0f)
        val y = ((image.height * fit * scale - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val transform = rememberTransformableState { centroid, scale, pan, _ ->
        val next = (zoom * scale).coerceIn(1f, 5f)
        val focus = if (centroid == Offset.Unspecified) Offset.Zero else centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        offset = bounded((offset - focus) * (next / zoom) + focus + pan, next)
        onZoom(next)
    }
    Column(modifier.testTag("report_preview")) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.reports_position, page + 1, count), Modifier.testTag("report_position"))
            Row {
                IconButton({ onPage(page - 1); offset = Offset.Zero }, enabled = page > 0, modifier = Modifier.testTag("report_previous")) {
                    WalkoryIcon(Icons.Rounded.ChevronLeft, description = stringResource(R.string.reports_previous))
                }
                IconButton({ onPage(page + 1); offset = Offset.Zero }, enabled = page + 1 < count, modifier = Modifier.testTag("report_next")) {
                    WalkoryIcon(Icons.Rounded.ChevronRight, description = stringResource(R.string.reports_next))
                }
                IconButton({ onZoom(if (zoom == 1f) 2f else 1f); offset = Offset.Zero }, Modifier.testTag("report_zoom")) {
                    WalkoryIcon(if (zoom == 1f) Icons.Rounded.ZoomIn else Icons.Rounded.ZoomOut,
                        description = stringResource(if (zoom == 1f) R.string.reports_zoom else R.string.reports_fit))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it; offset = bounded(offset, zoom) }
            .transformable(transform, canPan = { zoom > 1f }).testTag("report_page"), contentAlignment = Alignment.Center) {
            bitmap?.let { image ->
                Image(image.asImageBitmap(), stringResource(R.string.reports_position, page + 1, count),
                    Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y })
            }
            if (error) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WalkorySpacing.medium), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reports_preview_error), color = MaterialTheme.colorScheme.error)
                TextButton({ attempt++ }, Modifier.testTag("report_preview_retry")) { Text(stringResource(R.string.reports_retry)) }
            } else if (bitmap == null) CircularProgressIndicator()
        }
    }
}
