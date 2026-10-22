package io.github.sergioflatness.photoorderfix

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Version is shown on the help screen so testers can tell which build they installed.
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            var showHelp by rememberSaveable { mutableStateOf(false) }
            val prefs = remember { context.getSharedPreferences("app", Context.MODE_PRIVATE) }
            var noticeAccepted by remember { mutableStateOf(prefs.getBoolean(NOTICE_KEY, false)) }

            AnimatedContent(
                targetState = Screen(showHelp, state),
                // Progress updates keep the same key, so only a real change of screen animates.
                contentKey = { if (it.info) "info" else it.state::class },
                transitionSpec = { screenTransition(back = targetState.isBack(initialState)) },
                label = "screen",
            ) { screen ->
                if (screen.info) {
                    HelpScreen(version, onBack = { showHelp = false })
                } else when (val s = screen.state) {
                    UiState.Intro -> if (!noticeAccepted) {
                        NoticeScreen(onAccept = {
                            prefs.edit().putBoolean(NOTICE_KEY, true).apply()
                            noticeAccepted = true
                        })
                    } else {
                        HomeScreen(
                            undo = undo,
                            onUndo = viewModel::undo,
                            onHelp = { showHelp = true },
                            onScan = { permissionLauncher.launch(mediaPermissions()) },
                        )
                    }

                    UiState.Scanning -> ScanningScreen()

                    is UiState.Ready -> ReadyScreen(
                        summary = s.summary,
                        hasLocationAccess = hasLocationAccess,
                        canManageMedia = canManageMedia,
                        onBack = viewModel::restart,
                        onRequestLocation = { locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                        onManageMedia = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}"))
                            )
                        },
                        onFix = viewModel::fix,
                        onRepairDates = viewModel::repairDates,
                        onImportTakeout = { takeoutLauncher.launch(arrayOf("*/*")) },
                        onPickForDate = { pickLauncher.launch(arrayOf("image/*", "video/*")) },
                    )

                    is UiState.Working -> WorkingScreen(s.done, s.total, s.currentMs, s.undoing)

                    is UiState.Finished -> FinishedScreen(
                        moved = s.moved,
                        failed = s.failed,
                        cancelled = s.cancelled,
                        keptOriginals = s.keptOriginals,
                        years = s.years,
                        canUndo = undo != null,
                        onUndo = viewModel::undo,
                        onDone = viewModel::restart,
                    )

                    is UiState.Undone -> UndoneScreen(s.restored, s.missing, s.cancelled, onDone = viewModel::restart)

                    is UiState.DatesRepaired -> MessageScreen(
                        title = stringResource(R.string.dates_repaired, count(s.fixed)),
                        lines = listOfNotNull(
                            if (s.failed > 0) stringResource(R.string.dates_repair_failed, count(s.failed)) else null,
                            stringResource(R.string.dates_repaired_hint),
                        ),
                        onDone = viewModel::restart,
                    )

                    is UiState.TakeoutImported -> MessageScreen(
                        title = stringResource(R.string.takeout_title, count(s.fixed)),
                        lines = listOfNotNull(
                            stringResource(R.string.takeout_result, count(s.found), count(s.fixed)),
                            if (s.failed > 0) stringResource(R.string.takeout_failed, count(s.failed)) else null,
                            stringResource(R.string.takeout_skipped, count(s.alreadyEarlier), count(s.ambiguous), count(s.notOnPhone)),
                            if (s.fixed > 0) stringResource(R.string.takeout_next) else null,
                        ),
                        onDone = viewModel::restart,
                    )

                    is UiState.PickDate -> PickDateScreen(s, onApply = viewModel::applyPickedDate, onCancel = viewModel::restart)

                    is UiState.DatesSet -> MessageScreen(
                        title = stringResource(R.string.dates_set, count(s.fixed)),
                        lines = listOfNotNull(
                            if (s.failed > 0) stringResource(R.string.dates_set_failed, count(s.failed)) else null,
                            if (s.fixed > 0) stringResource(R.string.takeout_next) else null,
                        ),
                        onDone = viewModel::restart,
                    )

                    is UiState.Failed -> MessageScreen(
                        title = stringResource(R.string.error_title),
                        lines = listOf(stringResource(R.string.error, s.message)),
                        onDone = viewModel::restart,
                    )
                }
            }
        }
    }
}

/** Type a date for the photos picked under "other tools". */
@Composable
private fun PickDateScreen(s: UiState.PickDate, onApply: (String) -> Boolean, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    var text by remember(s) { mutableStateOf(s.initial) }
    var invalid by remember(s) { mutableStateOf(false) }
    Page(bottom = {
        GradientButton(stringResource(R.string.action_apply_date), onClick = { invalid = !onApply(text) })
    }) {
        TopBar(stringResource(R.string.tool_pick_title), onCancel)
        Headline(stringResource(R.string.pick_date_title, count(s.count)))
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.names.forEach { Text("• $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                label = { Text(stringResource(R.string.pick_date_label)) },
                isError = invalid,
                supportingText = { Text(stringResource(R.string.pick_date_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/** What is on screen: the help screen, or the current step of the fix. */
private data class Screen(val info: Boolean, val state: UiState) {
    // Closing help or returning to the start feels like going back, so it slides the other way.
    fun isBack(from: Screen): Boolean =
        (from.info && !info) || (!info && state == UiState.Intro && from.state != UiState.Intro)
}

private fun screenTransition(back: Boolean): ContentTransform {
    val sign = if (back) -1 else 1
    val slide = tween<IntOffset>(300)
    return ContentTransform(
        targetContentEnter = fadeIn(tween(300)) + slideInHorizontally(slide) { sign * it / 6 },
        initialContentExit = fadeOut(tween(200)) + slideOutHorizontally(slide) { -sign * it / 6 },
        sizeTransform = SizeTransform(clip = false),
    )
}

/** Set once the user has read the first-run notice. */
private const val NOTICE_KEY = "notice_accepted"
