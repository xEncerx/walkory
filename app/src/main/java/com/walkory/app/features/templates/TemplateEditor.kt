package com.walkory.app.features.templates

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.walkory.app.R
import com.walkory.app.ui_kit.*
import kotlinx.coroutines.launch

@Composable
internal fun TemplateEditor(
    state: TemplatesState,
    onRename: (String) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (String, String, String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onMoveItem: (String, String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
) {
    val draft = state.draft ?: return
    val saving = state.saveStatus == SaveStatus.SAVING
    var removeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxSize().testTag("template_editor")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = WalkorySpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                TemplateBack(onBack)
                Text(stringResource(if (draft.isNew) R.string.templates_new else R.string.templates_edit),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            }
            LazyColumn(Modifier.weight(1f).testTag("template_editor_list"), state = listState,
                contentPadding = PaddingValues(WalkorySpacing.medium), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                item("name") {
                    TemplateTextField(draft.name, onRename, stringResource(R.string.templates_name),
                        state.validation, saving, Modifier.testTag("template_name"))
                }
                item("checks_heading") {
                    Text(stringResource(R.string.templates_items), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                if (draft.checks.isEmpty()) item("empty") {
                    WalkoryMessage(stringResource(R.string.templates_first_title), stringResource(R.string.templates_first_body))
                    if (state.validation) Text(stringResource(R.string.templates_at_least_one), color = MaterialTheme.colorScheme.error)
                }
                itemsIndexed(draft.checks, key = { _, item -> item.key }) { index, item ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("template_item_$index")) {
                        Column(Modifier.padding(WalkorySpacing.medium), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ReorderHandle(item.key, listState, saving, onMoveItem)
                                Text(stringResource(R.string.templates_number, index + 1), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                IconButton({ onMoveItem(item.key, draft.checks[index - 1].key) }, enabled = !saving && index > 0,
                                    modifier = Modifier.testTag("template_up_$index")) {
                                    WalkoryIcon(Icons.Rounded.ArrowUpward, description = stringResource(R.string.templates_up))
                                }
                                IconButton({ onMoveItem(item.key, draft.checks[index + 1].key) }, enabled = !saving && index < draft.checks.lastIndex,
                                    modifier = Modifier.testTag("template_down_$index")) {
                                    WalkoryIcon(Icons.Rounded.ArrowDownward, description = stringResource(R.string.templates_down))
                                }
                            }
                            TemplateTextField(item.title, { onEditItem(item.key, it, item.guidance) }, stringResource(R.string.templates_item_title),
                                state.validation, saving, Modifier.testTag("template_item_title_$index"))
                            OutlinedTextField(item.guidance, { onEditItem(item.key, item.title, it) },
                                label = { Text(stringResource(R.string.templates_guidance)) }, enabled = !saving,
                                modifier = Modifier.fillMaxWidth().testTag("template_item_guidance_$index"))
                            TextButton({ removeKey = item.key }, enabled = !saving, modifier = Modifier.testTag("template_remove_$index")) {
                                WalkoryIcon(Icons.Rounded.Delete, description = null)
                                Text(stringResource(R.string.templates_remove_item))
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = WalkorySpacing.medium)) {
                WalkorySaveStatus(state.saveStatus, onSave)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                    OutlinedButton(onAddItem, enabled = !saving, modifier = Modifier.testTag("template_add_item")) { Text(stringResource(R.string.templates_add_item)) }
                    Button(onSave, enabled = !saving, modifier = Modifier.testTag("template_save")) { Text(stringResource(R.string.templates_save)) }
                    TextButton(onDiscard, enabled = !saving, modifier = Modifier.testTag("template_discard")) { Text(stringResource(R.string.templates_discard)) }
                }
            }
        }
    }
    if (removeKey != null) WalkoryConfirmation(stringResource(R.string.templates_remove_title), stringResource(R.string.templates_remove_body),
        stringResource(R.string.templates_remove_item), { removeKey?.let(onRemoveItem); removeKey = null }, { removeKey = null })
}

@Composable
private fun TemplateTextField(value: String, onChange: (String) -> Unit, label: String, validation: Boolean, saving: Boolean, modifier: Modifier) {
    val invalid = requiredTextError(value, validation)
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = !saving, isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.common_required_field)) }) else null,
        modifier = modifier.fillMaxWidth())
}

/** Drag works on visible item centers; explicit move controls provide the accessible alternative. */
@Composable
private fun ReorderHandle(key: String, list: LazyListState, disabled: Boolean, onMove: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val description = stringResource(R.string.templates_drag)
    Box(Modifier.sizeIn(minWidth = WalkoryLayout.minimumTouchTarget, minHeight = WalkoryLayout.minimumTouchTarget)
        .semantics { contentDescription = description }
        .pointerInput(key, disabled) {
            if (!disabled) {
                var center = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val item = list.layoutInfo.visibleItemsInfo.find { it.key == key }
                        center = item?.let { it.offset + it.size / 2f } ?: 0f
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        center += delta.y
                        val info = list.layoutInfo
                        val target = info.visibleItemsInfo.find { center >= it.offset && center <= it.offset + it.size }
                        if (target != null && target.key != key) move(key, target.key.toString())
                        val edge = WalkoryLayout.minimumTouchTarget.toPx()
                        val scroll = when {
                            center < info.viewportStartOffset + edge -> -edge / 3f
                            center > info.viewportEndOffset - edge -> edge / 3f
                            else -> 0f
                        }
                        if (scroll != 0f) scope.launch { list.scrollBy(scroll) }
                    },
                )
            }
        }, contentAlignment = Alignment.Center) {
        WalkoryIcon(Icons.Rounded.DragHandle, description = null)
    }
}
