package com.walkory.app.features.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.Alignment
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
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small),
            verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            photos.forEach { photo ->
                key(photo.id) {
                    val photoFile = remember(photo.fileId, model) { model.media.file(photo.fileId) }
                    OutlinedCard(Modifier.size(WalkoryLayout.photoThumbnail).clickable { selected = photo.id.value }.testTag("photo_${photo.id.value}")) {
                        Box(Modifier.fillMaxSize()) {
                            AsyncImage(photoFile, stringResource(R.string.inspections_media_open),
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            photo.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)) {
                                    Text(caption, Modifier.padding(WalkorySpacing.small), style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            if (owner is PhotographOwner.Condition) OutlinedCard(
                onClick = { model.requestSource(inspection, owner, newIssueDescription) },
                enabled = !state.busy && !preparing && !awaiting,
                modifier = Modifier.size(WalkoryLayout.photoThumbnail).testTag("media_add_condition"),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WalkoryIcon(Icons.Rounded.Add, description = stringResource(R.string.inspections_media_add))
                }
            }
        }
        if (owner is PhotographOwner.IssueEvidence) OutlinedButton({ model.requestSource(inspection, owner, newIssueDescription) },
            enabled = !state.busy && !preparing && !awaiting, modifier = Modifier.testTag("media_add_issue")) {
            Text(stringResource(R.string.inspections_media_add))
        }
    }
    if (selected != null && photos.isNotEmpty()) PhotographViewer(photos, selected!!, model.media::file, { selected = it },
        { selected = null }, { photo, caption -> onCaption(owner, photo, caption) }, { photo -> onDelete(owner, photo) }, saveStatus, onRetry)
    LaunchedEffect(photos.isEmpty()) { if (photos.isEmpty()) selected = null }
}
