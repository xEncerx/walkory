package com.walkory.app.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.walkory.app.domain.PropertyCard
import com.walkory.app.domain.PropertyId
import com.walkory.app.features.inspections.InspectionDiscovery
import com.walkory.app.features.objects.PropertiesBrowser
import com.walkory.app.features.objects.PropertiesState
import com.walkory.app.ui_kit.WalkoryTheme

@Preview(name = "Compact · English", widthDp = 412, heightDp = 892)
@Preview(name = "Compact · Russian · dark · large text", widthDp = 412, heightDp = 892, locale = "ru", fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expanded · English", widthDp = 1200, heightDp = 800)
@Composable
private fun ShellPreview() {
    WalkoryTheme {
        WalkoryShell(settingsContent = { _, _ -> androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.walkory.app.R.string.settings_title)) },
            objectsContent = { expanded, _ ->
            PropertiesBrowser(CollectionState.Content(listOf(PropertyCard(PropertyId("preview-home"), "Sample home", "Sample address"))),
                CollectionState.Content(emptyList()), null, expanded, {}, {}, PropertiesState(), {}, {}, {}, {})
        }, inspectionsContent = { expanded, _ ->
            com.walkory.app.features.inspections.InspectionBrowser(CollectionState.Content(emptyList()), null, expanded, {}, {}, InspectionDiscovery(), {}) {}
        }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.walkory.app.R.string.templates_title)) }
    }
}
