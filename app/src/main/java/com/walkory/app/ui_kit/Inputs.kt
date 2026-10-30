package com.walkory.app.ui_kit

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.View
import java.util.WeakHashMap

private class FocusedInput(var bounds: Rect = Rect.Zero, var focused: Boolean = false)
private val focusedInputs = WeakHashMap<View, FocusedInput>()

/** Observe without consuming the tap, so another action still receives its first click. */
fun Modifier.dismissKeyboardOnOutsideTap(): Modifier = composed {
    val view = LocalView.current
    val focus = LocalFocusManager.current
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    onGloballyPositioned { origin = it.boundsInWindow().topLeft }.pointerInput(view, focus) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val input = focusedInputs[view]
            if (input?.focused == true && !input.bounds.contains(down.position + origin)) focus.clearFocus()
        }
    }
}

@Composable
fun WalkoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    val input = remember { FocusedInput() }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(view) { onDispose { if (focusedInputs[view] === input) focusedInputs.remove(view) } }
    TextField(value, onValueChange,
        modifier.border(1.dp, when {
            isError -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }, MaterialTheme.shapes.large).onGloballyPositioned { input.bounds = it.boundsInWindow() }.onFocusChanged {
            input.focused = it.isFocused
            focused = it.isFocused
            if (it.isFocused) focusedInputs[view] = input
        },
        enabled = enabled, label = label, supportingText = supportingText, isError = isError,
        keyboardOptions = keyboardOptions, singleLine = singleLine, leadingIcon = leadingIcon,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

/** Measure actual labels: large type can require a vertical layout even in a wide window. */
@Composable
fun WalkoryEditorActions(
    saveLabel: String, cancelLabel: String, onSave: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val half = with(density) { ((maxWidth - WalkorySpacing.small) / 2).roundToPx() }
        val padding = with(density) { 48.dp.roundToPx() }
        val horizontal = measurer.measure(cancelLabel, style, softWrap = false).size.width + padding <= half &&
            measurer.measure(saveLabel, style, softWrap = false).size.width + padding <= half
        if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            OutlinedButton(onCancel, Modifier.weight(1f), enabled = enabled) { Text(cancelLabel) }
            Button(onSave, Modifier.weight(1f), enabled = enabled) { Text(saveLabel) }
        } else Column(verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            Button(onSave, Modifier.fillMaxWidth(), enabled = enabled) { Text(saveLabel) }
            OutlinedButton(onCancel, Modifier.fillMaxWidth(), enabled = enabled) { Text(cancelLabel) }
        }
    }
}
