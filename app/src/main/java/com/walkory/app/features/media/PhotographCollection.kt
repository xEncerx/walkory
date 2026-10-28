package com.walkory.app.features.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun PhotographFeedback(model: PhotographViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.inspections_media_importing))
    }
    state.error?.let { error ->
        Text(stringResource(when (error) {
            DomainError.InvalidImage -> R.string.inspections_media_invalid
            DomainError.InsufficientStorage -> R.string.inspections_media_storage_full
            DomainError.AttachmentCleanupPending -> R.string.inspections_cleanup_pending
            else -> R.string.inspections_media_failed
        }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("media_error"))
        Row {
            if (error == DomainError.AttachmentCleanupPending) TextButton(model::retryCleanup) { Text(stringResource(R.string.common_retry)) }
            TextButton(model::dismissError) { Text(stringResource(R.string.inspections_dismiss)) }
        }
    }
}

@Composable
internal fun PhotographCollection(
    inspection: InspectionId,
    owner: PhotographOwner,
    photos: List<Photograph>,
    model: PhotographViewModel,
    onCaption: (PhotographOwner, PhotographId, String) -> Boolean,
    onDelete: (PhotographOwner, PhotographId) -> Boolean,
    saveStatus: SaveStatus,
    onRetry: () -> Unit,
    newIssueDescription: String? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val preparing by model.preparingCamera.collectAsStateWithLifecycle()
    val awaiting by model.awaitingResult.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
        Text(stringResource(if (owner is PhotographOwner.Condition) R.string.inspections_media_condition else R.string.inspections_media_issue), style = MaterialTheme.typography.titleSmall)
        if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            items(photos, key = { it.id.value }) { photo ->
                val photoFile = remember(photo.fileId, model) { model.media.file(photo.fileId) }
                OutlinedCard(Modifier.width(WalkoryLayout.photoThumbnail).clickable { selected = photo.id.value }.testTag("photo_${photo.id.value}")) {
                    AsyncImage(photoFile, stringResource(R.string.inspections_media_open),
                        Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
                    photo.caption?.let { Text(it, Modifier.padding(WalkorySpacing.small)) }
                }
            }
        }
        OutlinedButton({ model.requestSource(inspection, owner, newIssueDescription) }, enabled = !state.busy && !preparing && !awaiting,
            modifier = Modifier.testTag(if (owner is PhotographOwner.Condition) "media_add_condition" else "media_add_issue")) {
            Text(stringResource(R.string.inspections_media_add))
        }
    }
    if (selected != null && photos.isNotEmpty()) PhotographViewer(photos, selected!!, model.media::file, { selected = it },
        { selected = null }, { photo, caption -> onCaption(owner, photo, caption) }, { photo -> onDelete(owner, photo) }, saveStatus, onRetry)
    LaunchedEffect(photos.isEmpty()) { if (photos.isEmpty()) selected = null }
}
