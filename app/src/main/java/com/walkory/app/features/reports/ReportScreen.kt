package com.walkory.app.features.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.createSavedStateHandle
import com.walkory.app.platform.pdf.ReportDelivery
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun rememberReportModel(engine: ReportEngine, settings: SettingsRepository, delivery: ReportDelivery): ReportViewModel {
    val factory = remember(engine, settings, delivery) { viewModelFactory { initializer { ReportViewModel(engine, settings, delivery, createSavedStateHandle()) } } }
    return viewModel(factory = factory)
}

@Composable
internal fun ReportScreen(inspection: Inspection, saved: Boolean, model: ReportViewModel) {
    val states by model.state.collectAsStateWithLifecycle()
    val state = states[inspection.id] ?: ReportState()
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    var requested by rememberSaveable(inspection.id.value, locale) { mutableStateOf(false) }
    val previewState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val canGenerate = saved && state.delivery != DeliveryStatus.CHOOSING && state.delivery != DeliveryStatus.SAVING
    LaunchedEffect(inspection, canGenerate, locale) {
        if (canGenerate) { model.generate(inspection.id, locale); requested = true }
    }
    if (requested && saved && state.report != null && !state.running && state.error == null && !state.cancelled) {
        previewState.SaveableStateProvider(state.report.fileId.value) {
            ReportDeliveryScreen(inspection, state, model)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WalkorySpacing.medium).testTag("report_setup"),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        if (!saved) Text(stringResource(R.string.reports_wait_saved))
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (state.running) {
                when (val progress = state.progress) {
                    is ReportProgress.Rendering -> {
                        Text(stringResource(R.string.reports_rendering, progress.completedChecks, progress.totalChecks))
                        LinearProgressIndicator(progress = { if (progress.totalChecks == 0) 0f else progress.completedChecks.toFloat() / progress.totalChecks }, modifier = Modifier.fillMaxWidth())
                    }
                    ReportProgress.Staging -> { Text(stringResource(R.string.reports_staging)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    ReportProgress.Finalizing -> { Text(stringResource(R.string.reports_finalizing)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            } else if (state.cancelled) Text(stringResource(R.string.reports_cancelled))
            else if (state.error != null) Text(stringResource(reportErrorLabel(state.error)), color = MaterialTheme.colorScheme.error)
            else if (!requested) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.running) OutlinedButton({ model.cancel(inspection.id) }, Modifier.testTag("report_cancel")) { Text(stringResource(R.string.reports_cancel)) }
        else if (state.error != null || state.cancelled) Button({ model.generate(inspection.id, locale) },
            Modifier.testTag("report_generate"), enabled = saved) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

internal fun reportErrorLabel(error: DomainError): Int = when (error) {
    DomainError.InvalidImage -> R.string.reports_error_photo
    DomainError.InsufficientStorage -> R.string.reports_error_full
    DomainError.StorageUnavailable -> R.string.reports_error_storage
    is DomainError.NotFound -> R.string.reports_error_missing
    else -> R.string.reports_error_generation
}
