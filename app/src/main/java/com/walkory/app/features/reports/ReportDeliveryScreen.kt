package com.walkory.app.features.reports

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.domain.Inspection
import com.walkory.app.platform.pdf.deliveryError
import com.walkory.app.platform.pdf.DeliveryError
import com.walkory.app.ui_kit.*

@Composable
internal fun ReportDeliveryScreen(inspection: Inspection, state: ReportState, model: ReportViewModel) {
    val report = requireNotNull(state.report)
    val context = LocalContext.current
    val busy = state.delivery == DeliveryStatus.CHOOSING || state.delivery == DeliveryStatus.SAVING
    Column(Modifier.fillMaxSize()) {
        ReportPreviewPane(report, model.delivery.file(report.fileId), Modifier.weight(1f).fillMaxWidth())
        Surface(tonalElevation = WalkorySpacing.small) {
            Column(Modifier.fillMaxWidth().padding(WalkorySpacing.medium), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                if (state.delivery == DeliveryStatus.SAVING) {
                    LinearProgressIndicator(progress = { if (state.total == 0L) 0f else state.copied.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    TextButton({ model.cancelSave(inspection.id) }, Modifier.testTag("report_save_cancel")) { Text(stringResource(R.string.common_cancel)) }
                }
                if (state.delivery == DeliveryStatus.FAILED) Text(stringResource(when (state.deliveryError) {
                    DeliveryError.FULL -> R.string.reports_save_full
                    DeliveryError.MISSING -> R.string.reports_artifact_missing
                    else -> R.string.reports_delivery_error
                }), color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                    OutlinedButton({
                        val name = inspection.details.name.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").take(80).trim().ifBlank { "Walkory" }
                        model.chooseDestination(inspection.id, "$name-${inspection.details.date}.pdf")
                    }, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight().testTag("report_save")) { Text(stringResource(R.string.reports_save)) }
                    Button({
                        try {
                            context.startActivity(Intent.createChooser(model.delivery.share(report.fileId), null))
                            model.dismissDeliveryError(inspection.id)
                        }
                        catch (failure: java.io.IOException) { model.deliveryFailed(inspection.id, deliveryError(failure)) }
                        catch (failure: RuntimeException) { model.deliveryFailed(inspection.id, deliveryError(failure)) }
                    }, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight().testTag("report_share")) { Text(stringResource(R.string.reports_share)) }
                }
            }
        }
    }
}
