package io.github.sergioflatness.photoorderfix

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** Card with a title row that opens and closes; the content slides down into place. */
@Composable
fun ExpandCard(title: String, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val easing = tween<Float>(400, easing = FastOutSlowInEasing)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 22.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = if (open) FontWeight.Bold else FontWeight.Medium)
            Text(if (open) "−" else "+", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(400, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
                fadeIn(tween(350, delayMillis = 80)) +
                slideInVertically(tween(400, easing = FastOutSlowInEasing)) { -it / 6 },
            exit = shrinkVertically(tween(350, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
                fadeOut(easing) +
                slideOutVertically(tween(350)) { -it / 6 },
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Scan result: how many are out of order, details, other tools, then "fix N". */
@Composable
fun ReadyScreen(
    summary: ScanSummary,
    hasLocationAccess: Boolean,
    canManageMedia: Boolean,
    onBack: () -> Unit,
    onRequestLocation: () -> Unit,
    onManageMedia: () -> Unit,
    onFix: () -> Unit,
    onRepairDates: () -> Unit,
    onImportTakeout: () -> Unit,
    onPickForDate: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val toFix = summary.toResave
    val lowStorage = toFix > 0 && !summary.canKeepOriginals
    Page(bottom = {
        if (lowStorage) LowStorageBanner()
        if (toFix > 0) {
            GradientButton(stringResource(R.string.action_fix, count(toFix)), onClick = onFix, enabled = hasLocationAccess)
            if (!lowStorage) Footnote(stringResource(R.string.fix_footnote))
        } else {
            GradientButton(stringResource(R.string.action_home), onClick = onBack)
        }
    }) {
        TopBar(stringResource(R.string.result_title), onBack)
        if (toFix > 0) {
            CountUpHeadline(toFix)
        } else {
            Headline(stringResource(R.string.nothing_to_do))
        }
        if (summary.partialAccess) {
            WarningBanner(stringResource(R.string.partial_access_title), stringResource(R.string.partial_access))
        }
        ChartCard(summary.totalItems, toFix, Formatter.formatShortFileSize(context, summary.resaveBytes))
        if (summary.datesToRepair > 0) {
            NoteCard(stringResource(R.string.dates_to_repair_title)) {
                Text(stringResource(R.string.dates_to_repair, count(summary.datesToRepair)), fontSize = 14.sp, lineHeight = 21.sp)
                TonalButton(
                    stringResource(R.string.action_repair_dates, count(summary.datesToRepair)),
                    onClick = onRepairDates,
                    height = 48.dp,
                    container = MaterialTheme.colorScheme.surface,
                )
            }
        }
        if (toFix > 0) ExpandCard(stringResource(R.string.details)) { Details(summary) }
        ExpandCard(stringResource(R.string.other_tools)) {
            Tool(stringResource(R.string.tool_takeout_title), stringResource(R.string.tool_takeout_body), stringResource(R.string.action_import_takeout), onImportTakeout)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Tool(stringResource(R.string.tool_pick_title), stringResource(R.string.tool_pick_body), stringResource(R.string.action_pick_date), onPickForDate)
        }
        if (toFix > 0 && !hasLocationAccess) {
            NoteCard(stringResource(R.string.location_title)) {
                Text(stringResource(R.string.location_needed), fontSize = 14.sp, lineHeight = 21.sp)
                TonalButton(stringResource(R.string.action_allow_location), onClick = onRequestLocation, height = 48.dp, container = MaterialTheme.colorScheme.surface)
            }
        }
        if (toFix > 0) {
            NoteCard(stringResource(R.string.before_fix_title)) {
                NoteLine(LineIcons.Shield, stringResource(R.string.before_fix_allow))
                NoteLine(LineIcons.Star, stringResource(R.string.before_fix_favorites))
                if (!canManageMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text(
                        stringResource(R.string.action_manage_media),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onManageMedia).padding(vertical = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** "176장이 / 순서가 어긋나 있어요", the number counting up when the screen opens. */
@Composable
private fun CountUpHeadline(n: Int) {
    val a = remember(n) { Animatable(0f) }
    LaunchedEffect(n) { a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Headline(stringResource(R.string.result_headline, count((n * a.value).toInt())))
}

/** Stacked bar: in place vs. to fix, out of everything found, plus the storage the copies need. */
@Composable
private fun ChartCard(total: Int, toFix: Int, size: String) {
    val kept = (total - toFix).coerceAtLeast(0)
    val grow = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(150)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
    }
    val fixColor = TileRamp[3]
    val keptColor = TileRamp[0]
    AppCard {
        Text(stringResource(R.string.chart_total, count(total)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // A few photos out of thousands would be invisible, so the fix part keeps a minimum width.
            val fixWeight = if (toFix == 0) 0f else maxOf(toFix.toFloat(), total * 0.05f)
            if (kept > 0) Box(Modifier.weight(kept.toFloat()).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth(grow.value.coerceAtLeast(0.001f)).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(keptColor))
            }
            if (toFix > 0) Box(
                Modifier
                    .weight(fixWeight)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(fixColor),
            )
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(keptColor, stringResource(R.string.chart_kept, count(kept)))
            if (toFix > 0) Legend(fixColor, stringResource(R.string.chart_fix, count(toFix)), Modifier.padding(start = 16.dp))
        }
        if (toFix > 0) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.space_needed), Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(size, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, Modifier.padding(start = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Out-of-order photos per year taken, and anything the app leaves alone. */
@Composable
private fun Details(summary: ScanSummary) {
    val years = summary.byYear
    if (years.isNotEmpty()) {
        Text(stringResource(R.string.details_by_year, count(summary.toResave)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val max = years.maxOf { it.second }
        val grow = remember { Animatable(0f) }
        LaunchedEffect(Unit) { delay(150); grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        years.forEachIndexed { i, (year, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.year_label, year), Modifier.width(56.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((n.toFloat() / max * grow.value).coerceAtLeast(0.001f)).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(rampColor(i, years.size)))
                }
                Text(stringResource(R.string.count_label, count(n)), Modifier.width(56.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val top = years.maxBy { it.second }.first
        Text(stringResource(R.string.details_top_year, top), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val notes = buildList {
        if (summary.movedToNewFolder > 0) add(stringResource(R.string.summary_moved, count(summary.movedToNewFolder)))
        if (summary.skipped > 0) add(stringResource(R.string.summary_skipped, count(summary.skipped)))
        if (summary.duplicateGroups > 0) add(stringResource(R.string.summary_duplicates, count(summary.duplicateGroups)))
    }
    notes.forEach { Text("• $it", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Tool(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    TonalButton(action, onClick = onClick)
}

/** Red banner above the button when there is no room to keep the originals in the trash. */
@Composable
private fun LowStorageBanner() = WarningBanner(stringResource(R.string.low_storage_title), stringResource(R.string.low_storage_body))

@Composable
fun WarningBanner(title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
            Text("!", color = MaterialTheme.colorScheme.errorContainer, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** Progress ring with the photo stack floating inside, the count, and the month being saved. */
@Composable
fun WorkingScreen(done: Int, total: Int, currentMs: Long?, undoing: Boolean = false) {
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(300), label = "progress")
    val pattern = stringResource(R.string.working_period_pattern)
    val zone = remember { ZoneId.systemDefault() }
    val year = currentMs?.let { Instant.ofEpochMilli(it).atZone(zone).year }
    Page {
        Headline(stringResource(if (undoing) R.string.working_undo_title else R.string.working_title), size = 28)
        VSpace(24.dp)
        AppCard(padding = 24.dp) {
            FitBox(300.dp, 280.dp) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                Box(Modifier.size(300.dp, 280.dp)) {
                    Canvas(Modifier.offset(13.dp, 3.dp).size(274.dp)) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2
                        val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                        if (progress > 0f) drawArc(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Blue, Violet)),
                            -90f, 360f * progress, false, Offset(inset, inset), arc,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    // Tile shade darkens as the work moves on; the label is the year being saved.
                    val shade = (progress * 4).toInt().coerceAtMost(3)
                    val label = year?.toString() ?: ""
                    Box(Modifier.size(300.dp, 280.dp).floating()) {
                        PhotoTile(label, shade, Modifier.offset(114.dp, 140.dp))
                        PhotoTile(label, shade, Modifier.offset(114.dp, 108.dp))
                        PhotoTile(label, shade, Modifier.offset(118.dp, 54.dp), rotation = -12f)
                    }
                }
            }
            Text(
                "${count(done)} / ${count(total)}",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            if (currentMs != null && !undoing) {
                val period = remember(currentMs, pattern) {
                    DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(currentMs).atZone(zone))
                }
                Text(
                    stringResource(R.string.working_period, period),
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.working_hint),
                Modifier.fillMaxWidth().padding(top = 12.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Cookie badge that pops in, with the check drawing itself. */
@Composable
fun DoneBadge(size: Int = 96) {
    val pop = remember { Animatable(0f) }
    val turn = remember { Animatable(-30f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
            launch { turn.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
            launch { delay(350); draw.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
        }
    }
    Box(
        Modifier
            .size(size.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = turn.value }
            .clip(CookieShape)
            .background(AccentGradient),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size((size * 0.46f).dp)) {
            val w = this.size.width
            val full = Path().apply {
                moveTo(w * 0.12f, w * 0.52f)
                lineTo(w * 0.40f, w * 0.78f)
                lineTo(w * 0.88f, w * 0.24f)
            }
            val measure = PathMeasure().apply { setPath(full, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * draw.value, part, true)
            drawPath(part, Color.White, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Done: badge, the years now in order, undo for 30 days, and the duplicates tip. */
@Composable
fun FinishedScreen(
    moved: Int,
    failed: Int,
    cancelled: Boolean,
    keptOriginals: Boolean,
    years: List<Int>,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Page(bottom = { GradientButton(stringResource(R.string.action_ok), onClick = onDone) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!cancelled) {
                DoneBadge()
                VSpace(12.dp)
            }
            Text(
                stringResource(if (cancelled) R.string.finished_cancelled_title else R.string.finished_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                if (cancelled) stringResource(R.string.finished_cancelled, count(moved)) else stringResource(R.string.finished_body),
                Modifier.padding(top = 12.dp),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (moved > 0) AppCard {
            val labels = years.takeLast(4).map { it.toString() }.ifEmpty { listOf("") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEachIndexed { i, label ->
                    val pop = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(600L + 120L * i); pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 350f)) }
                    Box(Modifier.weight(1f).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }) {
                        PhotoTile(label, (i + 4 - labels.size).coerceAtLeast(0), Modifier.fillMaxWidth(), width = 72.dp, height = 80.dp)
                    }
                }
                repeat(4 - labels.size) { Box(Modifier.weight(1f)) }
            }
            Text(
                stringResource(R.string.finished_saved, count(moved)),
                Modifier.fillMaxWidth().padding(top = 10.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (failed > 0) Text(stringResource(R.string.finished_failed, count(failed)), fontSize = 14.sp)
        if (keptOriginals && canUndo) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.finished_undo), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                TonalButton(stringResource(R.string.action_undo), onClick = onUndo, height = 56.dp)
            }
        }
        if (moved > 0) NoteCard(stringResource(R.string.duplicates_title)) {
            NoteLine(LineIcons.Copies, stringResource(R.string.duplicates_body))
        }
    }
}

@Composable
fun UndoneScreen(restored: Int, missing: Int, cancelled: Boolean, onDone: () -> Unit) {
    MessageScreen(
        title = if (cancelled) stringResource(R.string.undo_cancelled_title) else stringResource(R.string.undo_done, count(restored)),
        lines = listOfNotNull(
            if (cancelled) stringResource(R.string.undo_cancelled) else stringResource(R.string.undo_done_hint),
            if (missing > 0) stringResource(R.string.undo_missing, count(missing)) else null,
        ),
        onDone = onDone,
    )
}

/** Plain result screen: a title, a few lines in a card, and a button back to the start. */
@Composable
fun MessageScreen(title: String, lines: List<String>, onDone: () -> Unit, button: String = stringResource(R.string.action_home)) {
    Page(bottom = { GradientButton(button, onClick = onDone) }) {
        VSpace(24.dp)
        Headline(title)
        if (lines.isNotEmpty()) AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                lines.forEach { Text(it, fontSize = 15.sp, lineHeight = 22.sp) }
            }
        }
    }
}
