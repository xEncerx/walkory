package com.walkory.app.features.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

@Composable
internal fun rememberReportModel(engine: ReportEngine, settings: SettingsRepository): ReportViewModel {
    val factory = remember(engine, settings) { viewModelFactory { initializer { ReportViewModel(engine, settings) } } }
    return viewModel(factory = factory)
}

@Composable
internal fun ReportScreen(inspection: Inspection, saved: Boolean, model: ReportViewModel) {
    val states by model.state.collectAsStateWithLifecycle()
    val state = states[inspection.id] ?: ReportState()
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    val unchecked = remember(inspection) { inspection.progress().uncheckedChecks }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WalkorySpacing.medium).testTag("report_setup"),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        Text(inspection.details.name, style = MaterialTheme.typography.titleLarge)
        Text(inspection.details.property.address)
        Text(stringResource(R.string.reports_date, inspection.details.date.format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(java.util.Locale.forLanguageTag(locale)))))
        inspection.details.inspector?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.reports_inspector, it)) }
        Text(stringResource(R.string.reports_contents))
        if (unchecked > 0) Text(stringResource(R.string.reports_unchecked_notice))
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
            else if (state.error != null) Text(stringResource(when (state.error) {
                DomainError.InvalidImage -> R.string.reports_error_photo
                DomainError.InsufficientStorage -> R.string.reports_error_full
                DomainError.StorageUnavailable -> R.string.reports_error_storage
                is DomainError.NotFound -> R.string.reports_error_missing
                else -> R.string.reports_error_generation
            }), color = MaterialTheme.colorScheme.error)
            else state.report?.let { Text(pluralStringResource(R.plurals.reports_ready, it.pageCount, it.pageCount), Modifier.testTag("report_ready")) }
        }
        if (state.running) OutlinedButton({ model.cancel(inspection.id) }, Modifier.testTag("report_cancel")) { Text(stringResource(R.string.reports_cancel)) }
        else Button({ model.generate(inspection.id, locale) }, Modifier.testTag("report_generate"), enabled = saved) {
            Text(stringResource(if (state.report == null) R.string.reports_generate else R.string.reports_regenerate))
        }
    }
}
