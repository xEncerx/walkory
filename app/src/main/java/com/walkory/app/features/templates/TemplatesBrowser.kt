package com.walkory.app.features.templates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.walkory.app.R
import com.walkory.app.domain.*
import com.walkory.app.ui_kit.*

internal enum class TemplateFilter { ALL, OWN, BUILT_IN }

@Composable
internal fun TemplatesBrowser(
    state: TemplatesState, selectedId: String?, expanded: Boolean,
    filter: TemplateFilter, role: InspectionRole?, onFilter: (TemplateFilter) -> Unit, onRole: (InspectionRole?) -> Unit,
    onSelect: (String) -> Unit, onNew: () -> Unit, onEdit: (ChecklistTemplate) -> Unit, onCopy: (ChecklistTemplate) -> Unit,
    onDelete: (ChecklistTemplate) -> Unit, onRetry: () -> Unit, onBack: () -> Unit,
) {
    WalkoryListDetail(expanded, selectedId != null, list = {
        Column(Modifier.fillMaxSize().testTag("templates_catalog")) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = WalkorySpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                TemplateFilter.entries.forEach { option ->
                    FilterChip(filter == option, { onFilter(option) }, label = { Text(stringResource(when (option) {
                        TemplateFilter.ALL -> R.string.templates_all
                        TemplateFilter.OWN -> R.string.templates_own
                        TemplateFilter.BUILT_IN -> R.string.templates_builtin
                    })) }, modifier = Modifier.testTag("templates_filter_${option.name}"))
                }
            }
            if (filter != TemplateFilter.OWN) FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = WalkorySpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small),
            ) {
                FilterChip(role == null, { onRole(null) }, label = { Text(stringResource(R.string.templates_all_roles)) })
                InspectionRole.entries.forEach { option ->
                    FilterChip(role == option, { onRole(option) }, label = { Text(stringResource(if (option == InspectionRole.TENANT)
                        R.string.inspections_tenant else R.string.inspections_landlord)) }, modifier = Modifier.testTag("templates_role_${option.name}"))
                }
            }
            Button(onNew, Modifier.padding(horizontal = WalkorySpacing.medium).testTag("templates_new")) {
                Text(stringResource(R.string.templates_new))
            }
            LazyColumn(Modifier.weight(1f).testTag("templates_list"), contentPadding = PaddingValues(WalkorySpacing.medium),
                verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                when {
                    state.loading -> item { WalkoryLoading() }
                    state.loadFailed -> item { WalkoryError(onRetry) }
                    else -> {
                        val visible = state.templates.filter { template ->
                            val builtIn = template.origin as? TemplateOrigin.BuiltIn
                            (filter == TemplateFilter.ALL || (filter == TemplateFilter.OWN) == (builtIn == null)) &&
                                (filter == TemplateFilter.OWN || role == null || builtIn == null || builtIn.role == role)
                        }
                        if (visible.isEmpty()) item {
                            WalkoryMessage(stringResource(if (filter == TemplateFilter.OWN) R.string.templates_empty_title else R.string.templates_no_matches),
                                stringResource(if (filter == TemplateFilter.OWN) R.string.templates_empty_body else R.string.templates_no_matches_body))
                        }
                        items(visible, key = { it.id.value }) { template ->
                            val count = pluralStringResource(R.plurals.templates_checks, template.checks.size, template.checks.size)
                            WalkoryRecordRow(template.name,
                                if (template.origin == TemplateOrigin.Custom) stringResource(R.string.templates_custom_summary, count) else count,
                                template.id.value == selectedId, { onSelect(template.id.value) },
                                Modifier.fillMaxWidth().testTag("template_${template.id.value}"))
                        }
                    }
                }
            }
        }
    }, detail = {
        if (selectedId == null) Column(Modifier.padding(WalkorySpacing.large)) {
            WalkoryMessage(stringResource(R.string.common_select_title), stringResource(R.string.common_select_body))
        } else {
            val template = state.templates.find { it.id.value == selectedId }
            when {
                state.loading -> WalkoryLoading()
                state.loadFailed -> WalkoryError(onRetry)
                template == null -> TemplateMissing(onBack)
                else -> TemplateDetail(template, onEdit, onCopy, onDelete, onBack)
            }
        }
    })
}

@Composable
private fun TemplateDetail(template: ChecklistTemplate, onEdit: (ChecklistTemplate) -> Unit,
    onCopy: (ChecklistTemplate) -> Unit, onDelete: (ChecklistTemplate) -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("template_detail"), contentPadding = PaddingValues(WalkorySpacing.large),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        item { TemplateBack(onBack) }
        item { Text(template.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text(stringResource(if (template.origin is TemplateOrigin.BuiltIn) R.string.templates_builtin_notice else R.string.templates_own_notice)) }
        item { Button({ onCopy(template) }, Modifier.testTag("templates_copy")) { Text(stringResource(R.string.templates_copy)) } }
        if (template.origin == TemplateOrigin.Custom) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WalkorySpacing.small)) {
                OutlinedButton({ onEdit(template) }, Modifier.testTag("templates_edit")) { Text(stringResource(R.string.templates_edit)) }
                TextButton({ onDelete(template) }, Modifier.testTag("templates_delete")) { Text(stringResource(R.string.templates_delete)) }
            }
        }
        items(template.checks.size, key = { it }) { index ->
            val check = template.checks[index]
            WalkoryMessage(check.title, check.guidance.orEmpty())
        }
    }
}

@Composable
internal fun TemplateBack(onBack: () -> Unit) {
    TextButton(onBack, Modifier.testTag("templates_back")) {
        WalkoryIcon(Icons.AutoMirrored.Rounded.ArrowBack, description = null)
        Text(stringResource(R.string.common_back))
    }
}

@Composable
internal fun TemplateMissing(onBack: () -> Unit) {
    Column(Modifier.padding(WalkorySpacing.large), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
        TemplateBack(onBack)
        WalkoryMessage(stringResource(R.string.common_missing_title), stringResource(R.string.common_missing_body))
    }
}
