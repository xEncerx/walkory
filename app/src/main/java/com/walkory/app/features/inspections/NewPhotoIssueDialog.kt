package com.walkory.app.features.inspections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.features.media.*
import com.walkory.app.ui_kit.*

@Composable
internal fun NewPhotoIssueDialog(id: InspectionId, check: InspectionCheck, workflow: InspectionWorkflowViewModel,
    photographs: PhotographViewModel, onClose: () -> Unit) {
    val issueId = rememberSaveable { IssueId.new().value }
    var description by rememberSaveable { mutableStateOf("") }
    var emptyError by rememberSaveable { mutableStateOf(false) }
    val state by photographs.state.collectAsStateWithLifecycle()
    val records by workflow.state.collectAsStateWithLifecycle()
    val issue = check.issues.find { it.id.value == issueId }
    LaunchedEffect(issue?.id) { if (issue != null) emptyError = false }
    val owner = PhotographOwner.IssueEvidence(check.id, IssueId(issueId))
    AlertDialog(modifier = Modifier.dismissKeyboardOnOutsideTap(), onDismissRequest = onClose, title = { Text(stringResource(R.string.inspections_add_issue)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            WalkoryTextField(description, { description = it; emptyError = false }, label = { Text(stringResource(R.string.inspections_issue_description)) },
                modifier = Modifier.fillMaxWidth().testTag("result_edit_text"))
            if (emptyError) Text(stringResource(R.string.inspections_empty_issue), color = MaterialTheme.colorScheme.error)
            PhotographFeedback(photographs)
            PhotographCollection(id, owner, issue?.photos.orEmpty(), photographs,
                { selectedOwner, photo, caption -> workflow.caption(id, selectedOwner, photo, caption) },
                { selectedOwner, photo -> workflow.removePhoto(id, selectedOwner, photo) },
                records.records[id]?.saveStatus ?: SaveStatus.IDLE, { workflow.retry(id) },
                newIssueDescription = if (issue == null) description else null)
        } },
        confirmButton = { TextButton({
            val accepted = when {
                issue == null && description.isBlank() -> false
                issue == null -> workflow.addIssue(id, check.id, description)
                else -> workflow.editIssue(id, check.id, issue.id, description)
            }
            if (accepted) onClose() else emptyError = true
        }, enabled = !state.busy, modifier = Modifier.testTag("result_edit_save")) { Text(stringResource(R.string.inspections_save)) } },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.common_cancel)) } })
}
