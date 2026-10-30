package com.walkory.app.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.walkory.app.R
import com.walkory.app.ui_kit.WalkoryMessage
import com.walkory.app.ui_kit.WalkorySpacing

internal fun MainArea.titleResource(): Int = when (this) {
    MainArea.INSPECTIONS -> R.string.inspections_title
    MainArea.OBJECTS -> R.string.objects_title
    MainArea.TEMPLATES -> R.string.templates_title
    MainArea.SETTINGS -> R.string.settings_title
}


@Composable
internal fun SettingsScreen(information: Information?, model: com.walkory.app.features.settings.SettingsViewModel, onInformation: (Information) -> Unit) {
    if (information != null) InformationScreen(information)
    else com.walkory.app.features.settings.SettingsWorkspace(model, onInformation)
}



internal fun Information.titleResource(): Int = R.string.settings_about

@Composable
internal fun InformationScreen(page: Information) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val browserError = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val openGitHub: () -> Unit = {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://github.com/xEncerx/walkory")).apply {
            selector = android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_BROWSER)
        }
        try { context.startActivity(intent); browserError.value = false }
        catch (_: android.content.ActivityNotFoundException) { browserError.value = true }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("info_${page.name}"),
        contentPadding = PaddingValues(WalkorySpacing.large),
        verticalArrangement = Arrangement.spacedBy(WalkorySpacing.large),
    ) {
        item { AboutWalkoryCard(browserError.value, openGitHub) }
        item { WalkoryMessage(stringResource(R.string.common_help), stringResource(R.string.settings_workflow_body)) }
        item { WalkoryMessage(stringResource(R.string.settings_report_data), stringResource(R.string.settings_report_data_body)) }
    }
}

@Composable
private fun AboutWalkoryCard(browserError: Boolean, onGitHub: () -> Unit) {
    val body = androidx.compose.ui.platform.LocalResources.current.getText(R.string.settings_about_body) as android.text.Spanned
    val annotation = body.getSpans(0, body.length, android.text.Annotation::class.java).single { it.key == "github" }
    val accent = MaterialTheme.colorScheme.primary
    val description = remember(body, accent, onGitHub) {
        aboutDescription(body.toString(), body.getSpanStart(annotation) until body.getSpanEnd(annotation), accent, onGitHub)
    }
    val icon = remember(accent) {
        mapOf("github" to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.Center)) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                contentDescription = null, tint = accent, modifier = Modifier.fillMaxSize())
        })
    }
    androidx.compose.material3.ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(WalkorySpacing.large), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
            Text(stringResource(R.string.common_app_name), style = MaterialTheme.typography.titleLarge)
            Text(description, inlineContent = icon, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("settings_github"))
            if (browserError) Text(stringResource(R.string.settings_browser_missing), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun aboutDescription(text: String, linkRange: IntRange, accent: Color, onGitHub: () -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(text.substring(0, linkRange.first))
        withLink(LinkAnnotation.Clickable("github",
            styles = TextLinkStyles(style = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { onGitHub() })) {
            appendInlineContent("github", "GitHub")
            append('\u00A0')
            append(text.substring(linkRange.first, linkRange.last + 1))
        }
        append(text.substring(linkRange.last + 1))
    }
