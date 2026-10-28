package com.walkory.app.features.media

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun PhotographViewer(
    photos: List<Photograph>, selected: String, file: (LocalFileId) -> File,
    onSelected: (String) -> Unit, onClose: () -> Unit,
    onCaption: (PhotographId, String) -> Boolean, onDelete: (PhotographId) -> Boolean,
    saveStatus: SaveStatus, onRetry: () -> Unit,
) {
    val initial = photos.indexOfFirst { it.id.value == selected }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { photos.size }
    val scope = rememberCoroutineScope()
    var zoomPhoto by rememberSaveable { mutableStateOf(selected) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var caption by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var deletionError by rememberSaveable { mutableStateOf(false) }
    val photo = photos[pager.currentPage.coerceIn(photos.indices)]
    LaunchedEffect(photo.id) {
        if (zoomPhoto != photo.id.value) { zoomPhoto = photo.id.value; zoom = 1f }
    }
    LaunchedEffect(pager.currentPage, photos) { onSelected(photos[pager.currentPage.coerceIn(photos.indices)].id.value) }
    Dialog(onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        Surface(Modifier.fillMaxSize().testTag("photo_viewer")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                WalkoryTopAppBar(stringResource(R.string.inspections_media_position, pager.currentPage + 1, photos.size), onClose)
                WalkorySaveStatus(saveStatus, onRetry)
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { photos[it].id.value },
                    userScrollEnabled = zoom <= 1f || zoomPhoto != photo.id.value) { index ->
                    val page = photos[index]
                    val photoFile = remember(page.fileId) { file(page.fileId) }
                    ZoomablePhotograph(page, photoFile, if (zoomPhoto == page.id.value) zoom else 1f) {
                        zoomPhoto = page.id.value
                        zoom = it
                    }
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(WalkorySpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                    photo.caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                        OutlinedButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0,
                            modifier = Modifier.testTag("photo_previous")) { Text(stringResource(R.string.inspections_media_previous)) }
                        OutlinedButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < photos.lastIndex,
                            modifier = Modifier.testTag("photo_next")) { Text(stringResource(R.string.inspections_media_next)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                        TextButton({ caption = photo.caption.orEmpty(); editing = true }, Modifier.testTag("photo_caption")) { Text(stringResource(R.string.inspections_media_caption_action)) }
                        TextButton({ deletionError = false; deleting = true }, Modifier.testTag("photo_delete")) { Text(stringResource(R.string.inspections_media_delete)) }
                    }
                }
            }
        }
        if (editing) {
            AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.inspections_media_caption_action)) },
                text = { OutlinedTextField(caption, { caption = it }, label = { Text(stringResource(R.string.inspections_media_caption)) },
                    modifier = Modifier.fillMaxWidth().testTag("photo_caption_input")) },
                confirmButton = { TextButton({ if (onCaption(photo.id, caption)) editing = false }, Modifier.testTag("photo_caption_save")) { Text(stringResource(R.string.inspections_save)) } },
                dismissButton = { TextButton({ editing = false }) { Text(stringResource(R.string.common_cancel)) } })
        }
        if (deleting) WalkoryConfirmation(stringResource(R.string.inspections_media_delete),
            stringResource(if (deletionError) R.string.inspections_media_last_photo else R.string.inspections_media_delete_body),
            stringResource(R.string.inspections_media_delete), {
                if (onDelete(photo.id)) { deleting = false; if (photos.size == 1) onClose() }
                else deletionError = true
            }, { deleting = false })
    }
}

@Composable
private fun ZoomablePhotograph(photo: Photograph, file: File, scale: Float, onScale: (Float) -> Unit) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var unreadable by remember(file) { mutableStateOf(false) }
    fun bounded(value: Offset, zoom: Float = scale): Offset {
        val fit = if (imageSize.width > 0f && imageSize.height > 0f)
            minOf(size.width / imageSize.width, size.height / imageSize.height) else 1f
        val x = ((imageSize.width * fit * zoom - size.width) / 2f).coerceAtLeast(0f)
        val y = ((imageSize.height * fit * zoom - size.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val transform = rememberTransformableState { centroid, zoom, pan, _ ->
        val previousScale = scale
        val nextScale = (scale * zoom).coerceIn(1f, 5f)
        val focus = if (centroid == Offset.Unspecified) Offset.Zero else centroid - Offset(size.width / 2f, size.height / 2f)
        onScale(nextScale)
        offset = bounded((offset - focus) * (nextScale / previousScale) + focus + pan, nextScale)
    }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it; offset = bounded(offset) }
        .testTag("photo_zoom_${photo.id.value}").transformable(transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
        AsyncImage(file, photo.caption ?: stringResource(R.string.inspections_media_open),
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentScale = ContentScale.Fit, onError = { unreadable = true }, onSuccess = {
                unreadable = false
                imageSize = androidx.compose.ui.geometry.Size(it.result.image.width.toFloat(), it.result.image.height.toFloat())
                offset = bounded(offset)
            })
        if (unreadable) Text(stringResource(R.string.inspections_media_unreadable), Modifier.padding(WalkorySpacing.medium), color = MaterialTheme.colorScheme.error)
        if (scale > 1f) TextButton({ onScale(1f); offset = Offset.Zero }, Modifier.align(Alignment.TopEnd).testTag("photo_reset_zoom")) {
            Text(stringResource(R.string.inspections_media_reset_zoom))
        }
    }
}
