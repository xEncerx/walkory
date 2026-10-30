package com.walkory.app.features.objects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walkory.app.R
import com.walkory.app.app.CollectionState
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun PropertyEditor(state: PropertiesState, model: PropertiesViewModel) {
    val draft = state.draft ?: return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(WalkorySpacing.medium).testTag("property_editor"), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            WalkoryRequiredField(draft.name, { model.details(it, draft.address) }, stringResource(R.string.objects_name), state.validation,
                Modifier.testTag("property_name"))
            WalkoryRequiredField(draft.address, { model.details(draft.name, it) }, stringResource(R.string.objects_address), state.validation,
                Modifier.testTag("property_address"))
            Text(stringResource(R.string.objects_snapshot_notice))
            WalkorySaveStatus(state.status, model::save)
            Button(model::save, enabled = state.status != SaveStatus.SAVING, modifier = Modifier.testTag("property_save")) {
                Text(stringResource(if (draft.isNew) R.string.objects_create else R.string.inspections_save))
            }
        }
    }
}

@Composable
internal fun PropertyDiscardConfirmation(state: PropertiesState, model: PropertiesViewModel, onDiscard: () -> Unit) {
    if (state.confirmDiscard) WalkoryConfirmation(stringResource(R.string.objects_discard_title), stringResource(R.string.objects_discard_body),
        stringResource(R.string.objects_discard), { model.discard(); onDiscard() }, model::cancelDiscard)
}

/** Selection copies the current card address only for a new inspection, never for an existing one. */
@Composable
internal fun PropertyPicker(properties: CollectionState<PropertyCard>, selectedId: String?, model: PropertiesViewModel,
    onSelect: (PropertyCard?) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val dismiss: () -> Unit = { if (state.draft == null) onDismiss() else model.requestDiscard() }
    LaunchedEffect(state.savedId, properties) {
        val saved = (properties as? CollectionState.Content)?.items?.find { it.id.value == state.savedId }
        if (saved != null) { model.acknowledgeSaved(); onSelect(saved) }
    }
    AlertDialog(modifier = Modifier.dismissKeyboardOnOutsideTap(), onDismissRequest = dismiss,
        title = { Text(stringResource(if (state.draft != null) R.string.objects_new else R.string.objects_link)) },
        text = {
            if (state.draft != null) PropertyEditor(state, model)
            else Column(Modifier.verticalScroll(rememberScrollState()).testTag("property_picker"), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                WalkoryRecordRow(stringResource(R.string.objects_standalone), stringResource(R.string.objects_standalone_body), selectedId == null,
                    { onSelect(null) }, Modifier.testTag("property_none"))
                when (properties) {
                    CollectionState.Loading -> WalkoryLoading()
                    CollectionState.Error -> WalkoryError(onRetry)
                    is CollectionState.Content -> {
                        if (properties.items.isEmpty()) Text(stringResource(R.string.objects_empty_title))
                        properties.items.forEach { property ->
                            WalkoryRecordRow(property.name, property.address, selectedId == property.id.value, { onSelect(property) },
                                Modifier.testTag("property_pick_${property.id.value}"))
                        }
                    }
                }
                OutlinedButton({ model.begin() }, Modifier.testTag("property_picker_create")) { Text(stringResource(R.string.objects_new)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.common_cancel)) } },)
    PropertyDiscardConfirmation(state, model) {}
}
