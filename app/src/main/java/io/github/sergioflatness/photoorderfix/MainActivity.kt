package io.github.sergioflatness.photoorderfix

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            // Shown so testers can tell which build they installed.
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            if (version != null) Text("v$version", style = MaterialTheme.typography.bodySmall)

            when (val s = state) {
                UiState.Intro -> {
                    Text(stringResource(R.string.intro_body))
                    Button(onClick = { permissionLauncher.launch(mediaPermissions()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_scan))
                    }
                }

                UiState.Scanning -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scanning))
                }

                is UiState.Ready -> ReadyScreen(
                    summary = s.summary,
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

                is UiState.Working -> {
                    Text(stringResource(R.string.working, s.done, s.total))
                    LinearProgressIndicator(
                        progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.working_hint), style = MaterialTheme.typography.bodySmall)
                }

                is UiState.Finished -> {
                    Text(
                        if (s.cancelled) stringResource(R.string.finished_cancelled, s.moved)
                        else stringResource(R.string.finished_ok, s.moved)
                    )
                    if (s.failed > 0) Text(stringResource(R.string.finished_failed, s.failed))
                    Text(stringResource(R.string.finished_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }

                is UiState.DatesRepaired -> {
                    Text(stringResource(R.string.dates_repaired, s.fixed))
                    if (s.failed > 0) Text(stringResource(R.string.dates_repair_failed, s.failed))
                    Text(stringResource(R.string.dates_repaired_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }

                is UiState.TakeoutImported -> {
                    Text(stringResource(R.string.takeout_result, s.found, s.fixed), fontWeight = FontWeight.Bold)
                    if (s.failed > 0) Text(stringResource(R.string.takeout_failed, s.failed))
                    Text(
                        stringResource(R.string.takeout_skipped, s.alreadyEarlier, s.ambiguous, s.notOnPhone),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (s.fixed > 0) Text(stringResource(R.string.takeout_next))
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
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
                    Button(onClick = { invalid = !viewModel.applyPickedDate(text) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_apply_date))
                    }
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.action_cancel)) }
                }

                is UiState.DatesSet -> {
                    Text(stringResource(R.string.dates_set, s.fixed), fontWeight = FontWeight.Bold)
                    if (s.failed > 0) Text(stringResource(R.string.dates_set_failed, s.failed))
                    if (s.fixed > 0) Text(stringResource(R.string.takeout_next))
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }

                is UiState.Failed -> {
                    Text(stringResource(R.string.error, s.message))
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.action_again)) }
                }
            }
        }
    }
}

@Composable
private fun ReadyScreen(
    summary: ScanSummary,
    canManageMedia: Boolean,
    hasLocationAccess: Boolean,
    onRequestLocation: () -> Unit,
    onFix: () -> Unit,
    onRepairDates: () -> Unit,
    onImportTakeout: () -> Unit,
    onPickForDate: () -> Unit,
    onManageMedia: () -> Unit,
) {
    val context = LocalContext.current
    if (summary.partialAccess) {
        Card { Text(stringResource(R.string.partial_access), Modifier.padding(16.dp)) }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.summary_total, summary.totalItems))
            Text(stringResource(R.string.summary_dates, summary.fromCaptureDate, summary.fromFileName, summary.fromModifiedTime))
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
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dates_to_repair, summary.datesToRepair))
                Button(onClick = onRepairDates, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_repair_dates, summary.datesToRepair))
                }
            }
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.takeout_intro), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onImportTakeout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_import_takeout))
            }
            Text(stringResource(R.string.pick_date_intro), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onPickForDate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_pick_date))
            }
        }
    }

    if (summary.toResave == 0) {
        Text(stringResource(R.string.nothing_to_do), fontWeight = FontWeight.Bold)
        return
    }

    Text(
        stringResource(
            R.string.summary_resave,
            summary.toResave,
            Formatter.formatShortFileSize(context, summary.resaveBytes),
            Formatter.formatShortFileSize(context, summary.freeBytes),
        ),
        fontWeight = FontWeight.Bold,
    )
    if (summary.movedToNewFolder > 0) {
        Text(stringResource(R.string.summary_moved, summary.movedToNewFolder))
    }
    Card { Text(stringResource(R.string.cloud_warning), Modifier.padding(16.dp)) }
    Text(stringResource(R.string.how_it_works), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.caveats), style = MaterialTheme.typography.bodySmall)

    if (!hasLocationAccess) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.location_needed), fontWeight = FontWeight.Bold)
                Button(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_allow_location))
                }
            }
        }
    }
    if (!canManageMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        OutlinedButton(onClick = onManageMedia, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_manage_media))
        }
    }
    Button(onClick = onFix, enabled = hasLocationAccess, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_fix, summary.toResave))
    }
}
