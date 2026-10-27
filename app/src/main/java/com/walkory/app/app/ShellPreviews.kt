package com.walkory.app.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.walkory.app.domain.ChecklistTemplate
import com.walkory.app.domain.PropertyCard
import com.walkory.app.domain.PropertyId
import com.walkory.app.domain.TemplateCheck
import com.walkory.app.domain.TemplateId
import com.walkory.app.domain.TemplateOrigin
import com.walkory.app.ui_kit.WalkoryTheme

@Preview(name = "Compact · English", widthDp = 412, heightDp = 892)
@Preview(name = "Compact · Russian · dark · large text", widthDp = 412, heightDp = 892, locale = "ru", fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expanded · English", widthDp = 1200, heightDp = 800)
@Composable
private fun ShellPreview() {
    WalkoryTheme {
        WalkoryShell(ShellState(
            inspections = CollectionState.Content(emptyList()),
            objects = CollectionState.Content(listOf(PropertyCard(PropertyId("preview-home"), "Sample home", "Sample address"))),
            templates = CollectionState.Content(listOf(ChecklistTemplate(
                TemplateId("preview-template"), "Sample checklist", TemplateOrigin.Custom,
                listOf(TemplateCheck("Floor condition")),
            ))),
        ), {})
    }
}
