package com.walkory.app.features.media

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walkory.app.R
import com.walkory.app.domain.DomainError
import java.io.IOException

/** Unconditional root registration receives results even before Room/navigation have restored. */
@Composable
internal fun PhotographHost(model: PhotographViewModel) {
    val context = LocalContext.current
    val choosing by model.sourceMenu.collectAsStateWithLifecycle()
    val preparing by model.preparingCamera.collectAsStateWithLifecycle()
    val unavailable by model.cameraUnavailable.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), model::picked)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), model::captured)
    val pick: () -> Unit = {
        model.beginPicker()
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: ActivityNotFoundException) { model.launchFailed(camera = false) }
        catch (_: SecurityException) { model.launchFailed(camera = false, DomainError.InvalidImage) }
    }
    LaunchedEffect(preparing) {
        if (preparing) {
            try {
                val uri = model.media.prepareCapture()
                model.capturePrepared(uri)
                camera.launch(uri)
            } catch (_: ActivityNotFoundException) { model.launchFailed(camera = true) }
            catch (_: SecurityException) { model.launchFailed(camera = true) }
            catch (_: IOException) { model.launchFailed(camera = false) }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = model::dismissSource, title = { Text(stringResource(R.string.inspections_media_add)) },
        text = { Column {
            TextButton(model::chooseCamera, Modifier.fillMaxWidth().testTag("media_camera")) { Text(stringResource(R.string.inspections_media_camera)) }
            TextButton(pick, Modifier.fillMaxWidth().testTag("media_picker")) { Text(stringResource(R.string.inspections_media_picker)) }
        } }, confirmButton = { TextButton(model::dismissSource) { Text(stringResource(R.string.common_cancel)) } })
    if (unavailable) AlertDialog(onDismissRequest = model::dismissCameraError, title = { Text(stringResource(R.string.inspections_media_camera_unavailable)) },
        text = { Column {
            Text(stringResource(R.string.inspections_media_camera_help))
            TextButton({
                try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
                catch (_: ActivityNotFoundException) { model.fail() }
            }) { Text(stringResource(R.string.inspections_media_settings)) }
        } }, confirmButton = { TextButton(pick) { Text(stringResource(R.string.inspections_media_picker)) } },
        dismissButton = { TextButton(model::dismissCameraError) { Text(stringResource(R.string.common_cancel)) } })
}
