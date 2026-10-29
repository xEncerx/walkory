package com.walkory.app.features.reports

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.walkory.app.R
import com.walkory.app.domain.Inspection
import com.walkory.app.platform.pdf.deliveryError
import com.walkory.app.platform.pdf.DeliveryError
import com.walkory.app.ui_kit.WalkorySpacing

@Composable
internal fun ReportDeliveryScreen(inspection: Inspection, saved: Boolean, state: ReportState,
    model: ReportViewModel, locale: String, onBack: () -> Unit) {
    val report = requireNotNull(state.report)
    var page by rememberSaveable(report.fileId.value) { mutableIntStateOf(0) }
    var zoom by rememberSaveable(report.fileId.value, page) { mutableFloatStateOf(1f) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(WalkorySpacing.medium)) {
        if (maxWidth > maxHeight) {
            // Short windows keep the document and independently scrolling actions side by side.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                ReportPreviewPane(report, model.delivery.file(report.fileId), page, zoom, { page = it }, { zoom = it }, Modifier.weight(1f).fillMaxHeight())
                ReportActions(inspection, saved, state, model, locale, onBack, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            val controlsHeight = maxHeight * 0.5f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                ReportPreviewPane(report, model.delivery.file(report.fileId), page, zoom, { page = it }, { zoom = it }, Modifier.weight(1f).fillMaxWidth())
                ReportActions(inspection, saved, state, model, locale, onBack, Modifier.fillMaxWidth().heightIn(max = controlsHeight))
            }
        }
    }
}

@Composable
private fun ReportActions(inspection: Inspection, saved: Boolean, state: ReportState,
    model: ReportViewModel, locale: String, onBack: () -> Unit, modifier: Modifier) {
    val report = requireNotNull(state.report)
    val context = LocalContext.current
    val busy = state.delivery == DeliveryStatus.CHOOSING || state.delivery == DeliveryStatus.SAVING
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
        Text(pluralStringResource(R.plurals.reports_ready, report.pageCount, report.pageCount), Modifier.testTag("report_ready"))
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("report_delivery_status")) {
            if (state.delivery == DeliveryStatus.SAVING) {
                Text(stringResource(R.string.reports_saving))
                LinearProgressIndicator(progress = { if (state.total == 0L) 0f else state.copied.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                TextButton({ model.cancelSave(inspection.id) }, Modifier.testTag("report_save_cancel")) { Text(stringResource(R.string.common_cancel)) }
            }
            val label = when (state.delivery) {
                DeliveryStatus.SAVED -> R.string.reports_saved
                DeliveryStatus.CANCELLED -> R.string.reports_save_cancelled
                DeliveryStatus.SHARE_OPENED -> R.string.reports_share_opened
                DeliveryStatus.FAILED -> when (state.deliveryError) {
                    DeliveryError.FULL -> R.string.reports_save_full
                    DeliveryError.MISSING -> R.string.reports_artifact_missing
                    else -> R.string.reports_delivery_error
                }
                else -> null
            }
            label?.let { Text(stringResource(it), color = if (state.delivery == DeliveryStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
            if (state.cancelled) Text(stringResource(R.string.reports_cancelled))
            if (state.error != null) Text(stringResource(reportErrorLabel(state.error)), color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
            OutlinedButton({
                val name = inspection.details.name.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").take(80).trim().ifBlank { "Walkory" }
                model.chooseDestination(inspection.id, "$name-${inspection.details.date}.pdf")
            }, enabled = !busy, modifier = Modifier.testTag("report_save")) { Text(stringResource(R.string.reports_save)) }
            Button({
                try {
                    val send = model.delivery.share(report.fileId)
                    context.startActivity(Intent.createChooser(send, null))
                    model.shareOpened(inspection.id)
                } catch (failure: java.io.IOException) { model.deliveryFailed(inspection.id, deliveryError(failure)) }
                catch (failure: RuntimeException) { model.deliveryFailed(inspection.id, deliveryError(failure)) }
            }, enabled = !busy, modifier = Modifier.testTag("report_share")) { Text(stringResource(R.string.reports_share)) }
            TextButton({ model.generate(inspection.id, locale) }, enabled = !busy && saved,
                modifier = Modifier.testTag("report_generate")) { Text(stringResource(R.string.reports_regenerate)) }
            TextButton(onBack, Modifier.testTag("report_return")) { Text(stringResource(R.string.reports_return)) }
        }
    }
}
