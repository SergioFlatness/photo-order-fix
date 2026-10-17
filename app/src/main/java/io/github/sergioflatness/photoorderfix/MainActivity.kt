package io.github.sergioflatness.photoorderfix

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                App(viewModel)
            }
        }
    }
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
} + Manifest.permission.ACCESS_MEDIA_LOCATION

@Composable
private fun App(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val consent by viewModel.consentRequest.collectAsStateWithLifecycle()
    val canManageMedia by viewModel.canManageMedia.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val keepOriginals by viewModel.keepOriginals.collectAsStateWithLifecycle()

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        if (full || partial) viewModel.scan(partialAccess = !full)
    }

    var hasLocationAccess by remember { mutableStateOf(granted(Manifest.permission.ACCESS_MEDIA_LOCATION)) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasLocationAccess = ok
        // Android stops showing the dialog after the user declines twice, so send them to settings.
        if (!ok) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    val takeoutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importTakeout(uris)
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.pickForDate(uris)
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(consent) {
        consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshManageMedia()
        hasLocationAccess = granted(Manifest.permission.ACCESS_MEDIA_LOCATION)
        onPauseOrDispose { }
    }

    // A transparent container gives Scaffold no content color of its own (it falls back to black), so set it.
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.background(backgroundBrush()),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Version is shown so testers can tell which build they installed.
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            Header(version)

            when (val s = state) {
                UiState.Intro -> {
                    undo?.let { UndoCard(it, onUndo = viewModel::undo) }
                    IntroScreen(onScan = { permissionLauncher.launch(mediaPermissions()) })
                }

                UiState.Scanning -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scanning))
                }

                is UiState.Ready -> ReadyScreen(
                    summary = s.summary,
                    keepOriginals = keepOriginals,
                    onKeepOriginals = viewModel::setKeepOriginals,
                    canManageMedia = canManageMedia,
                    hasLocationAccess = hasLocationAccess,
                    onRequestLocation = { locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                    onFix = viewModel::fix,
                    onRepairDates = viewModel::repairDates,
                    onPickForDate = { pickLauncher.launch(arrayOf("image/*", "video/*")) },
                    onImportTakeout = { takeoutLauncher.launch(arrayOf("*/*")) },
                    onManageMedia = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}"))
                        )
                    },
                )

                is UiState.Working -> WorkingScreen(s.done, s.total, s.currentMs, s.undoing)

                is UiState.Finished -> FinishedScreen(s.moved, s.failed, s.cancelled, s.keptOriginals, onDone = viewModel::restart)

                is UiState.Undone -> UndoneScreen(s.restored, s.missing, s.cancelled, onDone = viewModel::restart)

                is UiState.DatesRepaired -> {
                    Text(stringResource(R.string.dates_repaired, s.fixed))
                    if (s.failed > 0) Text(stringResource(R.string.dates_repair_failed, s.failed))
                    Text(stringResource(R.string.dates_repaired_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(shape = outlinedShape, onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }

                is UiState.TakeoutImported -> {
                    Text(stringResource(R.string.takeout_result, s.found, s.fixed), fontWeight = FontWeight.Bold)
                    if (s.failed > 0) Text(stringResource(R.string.takeout_failed, s.failed))
                    Text(
                        stringResource(R.string.takeout_skipped, s.alreadyEarlier, s.ambiguous, s.notOnPhone),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (s.fixed > 0) Text(stringResource(R.string.takeout_next))
                    OutlinedButton(shape = outlinedShape, onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }

                is UiState.PickDate -> {
                    var text by remember(s) { mutableStateOf(s.initial) }
                    var invalid by remember(s) { mutableStateOf(false) }
                    Text(stringResource(R.string.pick_date_title, s.count), fontWeight = FontWeight.Bold)
                    s.names.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; invalid = false },
                        label = { Text(stringResource(R.string.pick_date_label)) },
                        isError = invalid,
                        supportingText = { Text(stringResource(R.string.pick_date_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GradientButton(stringResource(R.string.action_apply_date), onClick = { invalid = !viewModel.applyPickedDate(text) })
                    OutlinedButton(shape = outlinedShape, onClick = viewModel::restart) { Text(stringResource(R.string.action_cancel)) }
                }

                is UiState.DatesSet -> {
                    Text(stringResource(R.string.dates_set, s.fixed), fontWeight = FontWeight.Bold)
                    if (s.failed > 0) Text(stringResource(R.string.dates_set_failed, s.failed))
                    if (s.fixed > 0) Text(stringResource(R.string.takeout_next))
                    OutlinedButton(shape = outlinedShape, onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }

                is UiState.Failed -> {
                    Text(stringResource(R.string.error, s.message))
                    OutlinedButton(shape = outlinedShape, onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }
            }
        }
    }
}

@Composable
private fun ReadyScreen(
    summary: ScanSummary,
    keepOriginals: Boolean,
    onKeepOriginals: (Boolean) -> Unit,
    canManageMedia: Boolean,
    hasLocationAccess: Boolean,
    onRequestLocation: () -> Unit,
    onFix: () -> Unit,
    onRepairDates: () -> Unit,
    onImportTakeout: () -> Unit,
    onPickForDate: () -> Unit,
    onManageMedia: () -> Unit,
) {
    if (summary.partialAccess) {
        Card(colors = cardColors(), elevation = cardElevation()) { Text(stringResource(R.string.partial_access), Modifier.padding(16.dp)) }
    }
    SummaryCard(summary)
    if (summary.duplicateGroups > 0 || summary.skipped > 0) Card(colors = cardColors(), elevation = cardElevation()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (summary.duplicateGroups > 0) {
                Text(stringResource(R.string.summary_duplicates, summary.duplicateGroups))
                summary.duplicateExamples.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (summary.skipped > 0) {
                Text(stringResource(R.string.summary_skipped, summary.skipped))
                summary.skippedFolders.forEach { (folder, count) ->
                    Text("• $folder  ($count)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (summary.datesToRepair > 0) {
        Card(colors = cardColors(), elevation = cardElevation()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dates_to_repair, summary.datesToRepair))
                GradientButton(stringResource(R.string.action_repair_dates, summary.datesToRepair), onClick = onRepairDates)
            }
        }
    }

    Card(colors = cardColors(), elevation = cardElevation()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.takeout_intro), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(shape = outlinedShape, onClick = onImportTakeout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_import_takeout))
            }
            Text(stringResource(R.string.pick_date_intro), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(shape = outlinedShape, onClick = onPickForDate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_pick_date))
            }
        }
    }

    // SummaryCard already says there is nothing to do.
    if (summary.toResave == 0) return

    StorageCard(summary.resaveBytes, summary.freeBytes)
    if (summary.movedToNewFolder > 0) {
        Text(stringResource(R.string.summary_moved, summary.movedToNewFolder))
    }
    KeepOriginalsCard(keepOriginals, summary.canKeepOriginals, summary.resaveBytes, onKeepOriginals)
    Notice(stringResource(R.string.cloud_warning))
    Text(stringResource(R.string.how_it_works), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.caveats), style = MaterialTheme.typography.bodySmall)

    if (!hasLocationAccess) {
        Card(colors = cardColors(), elevation = cardElevation()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.location_needed), fontWeight = FontWeight.Bold)
                GradientButton(stringResource(R.string.action_allow_location), onClick = onRequestLocation)
            }
        }
    }
    if (!canManageMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        OutlinedButton(shape = outlinedShape, onClick = onManageMedia, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_manage_media))
        }
    }
    GradientButton(stringResource(R.string.action_fix, summary.toResave), onClick = onFix, enabled = hasLocationAccess)
}
