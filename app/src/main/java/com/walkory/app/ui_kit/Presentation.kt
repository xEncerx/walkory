package com.walkory.app.ui_kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.walkory.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkoryTopAppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.testTag("app_bar_title").semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) IconButton(onBack, Modifier.testTag("navigate_back")) {
                WalkoryIcon(Icons.AutoMirrored.Rounded.ArrowBack, description = stringResource(R.string.common_back))
            }
        },
        actions = actions,
    )
}

/** Validate after trimming, without rewriting the user's draft as they type. */
fun requiredTextError(value: String, showError: Boolean): Boolean = showError && value.isBlank()

@Composable
fun WalkoryRequiredField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showError: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val invalid = requiredTextError(value, showError)
    WalkoryTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.common_required_field)) }) else null,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun WalkoryMessage(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(WalkorySpacing.large), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            action?.invoke()
        }
    }
}

@Composable
fun WalkoryLoading() {
    Column(Modifier.padding(WalkorySpacing.large), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.common_loading))
    }
}

@Composable
fun WalkoryError(onRetry: () -> Unit) {
    WalkoryMessage(stringResource(R.string.common_error_title), stringResource(R.string.common_error_body)) {
        Button(onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

@Composable
fun WalkorySaveStatus(status: SaveStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (status == SaveStatus.IDLE) return
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(when (status) {
            SaveStatus.SAVING -> R.string.common_saving
            SaveStatus.SAVED -> R.string.common_saved
            SaveStatus.ERROR -> R.string.common_save_error
            SaveStatus.IDLE -> error("Idle has no label")
        }))
        if (status == SaveStatus.ERROR) TextButton(onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

/** The caller owns visibility/context; dismissal never implies confirmation. */
@Composable
fun WalkoryConfirmation(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(message) } },
        confirmButton = { TextButton(onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
