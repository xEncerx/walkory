package com.walkory.app.ui_kit

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.walkory.app.R

@Preview(name = "States · English", widthDp = 412)
@Preview(name = "States · Russian · dark · large text", widthDp = 412, locale = "ru", fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FoundationStatesPreview() {
    WalkoryTheme {
        Surface {
            Column(Modifier.padding(WalkorySpacing.medium), verticalArrangement = Arrangement.spacedBy(WalkorySpacing.medium)) {
                WalkoryRequiredField("  ", {}, stringResource(R.string.inspections_title), showError = true)
                WalkorySaveStatus(SaveStatus.SAVING, {})
                WalkorySaveStatus(SaveStatus.ERROR, {})
                WalkoryError({})
            }
        }
    }
}

@Preview(name = "Confirmation · Russian", locale = "ru", widthDp = 412)
@Composable
private fun FoundationConfirmationPreview() {
    WalkoryTheme {
        WalkoryConfirmation(
            title = stringResource(R.string.common_cancel),
            message = stringResource(R.string.settings_report_data_body),
            confirmLabel = stringResource(R.string.common_cancel),
            onConfirm = {}, onDismiss = {},
        )
    }
}
