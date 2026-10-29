package com.walkory.app.features.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Register before navigation/data restoration so a system result retains its original owner. */
@Composable
internal fun ReportHost(model: ReportViewModel) {
    val request by model.saveRequest.collectAsStateWithLifecycle()
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        model.destinationResult(it?.toString())
    }
    LaunchedEffect(request) {
        request?.takeUnless { it.launched }?.let {
            model.destinationLaunched()
            try { destination.launch(it.name) }
            catch (_: RuntimeException) { model.destinationFailed() }
        }
    }
}
