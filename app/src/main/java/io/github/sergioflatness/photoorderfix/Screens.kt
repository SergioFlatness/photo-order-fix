package io.github.sergioflatness.photoorderfix

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sergioflatness.photoorderfix.core.UndoLog
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Faded = Color(0xFF8EA8FF)
private val Fainter = Color(0xFFB9C9FF)

private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** Big headline with the number to fix in the gradient, then the found/fix counts and how dates were found. */
@Composable
fun SummaryCard(summary: ScanSummary) {
    if (summary.toResave > 0) {
        val fix = count(summary.toResave)
        val line = stringResource(R.string.ready_headline, fix)
        val start = line.indexOf(fix)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.ready_headline_total, count(summary.totalItems)))
                append("\n")
                append(line.substring(0, start))
                withStyle(SpanStyle(brush = AccentGradient)) { append(fix) }
                append(line.substring(start + fix.length))
            },
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    } else {
        Text(stringResource(R.string.nothing_to_do), fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
    }

    Card(colors = cardColors(), elevation = cardElevation(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(count(summary.totalItems), stringResource(R.string.stat_found))
                Stat(count(summary.toResave), stringResource(R.string.stat_to_resave), highlight = true)
            }
            val sources = listOf(
                summary.fromCaptureDate to MaterialTheme.colorScheme.primary,
                summary.fromFileName to MaterialTheme.colorScheme.secondary,
                summary.fromModifiedTime to MaterialTheme.colorScheme.outline,
            )
            val total = sources.sumOf { it.first }
            if (total > 0) {
                Text(stringResource(R.string.date_sources_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                ) {
                    sources.filter { it.first > 0 }.forEach { (n, color) ->
                        Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color))
                    }
                }
                val labels = listOf(R.string.source_capture, R.string.source_file_name, R.string.source_file_time)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    sources.zip(labels).forEach { (source, label) ->
                        Text(
                            "● " + stringResource(label, source.first * 100 / total),
                            color = source.second,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String, highlight: Boolean = false) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How much of the free space the copies need, as a gauge. */
@Composable
fun StorageCard(resaveBytes: Long, freeBytes: Long) {
    val context = LocalContext.current
    val share = if (freeBytes <= 0) 1f else (resaveBytes.toFloat() / freeBytes).coerceIn(0f, 1f)
    Card(colors = cardColors(), elevation = cardElevation(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.space_needed))
                Text(Formatter.formatShortFileSize(context, resaveBytes), fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(AccentGradient))
            }
            Text(
                stringResource(R.string.space_hint, Formatter.formatShortFileSize(context, freeBytes), (share * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tinted note, for advice rather than results. */
@Composable
fun Notice(text: String) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun WorkingScreen(done: Int, total: Int, currentMs: Long?, undoing: Boolean = false) {
    val progress = if (total == 0) 0f else done.toFloat() / total
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(210.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    AccentGradient, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(progress * 100).toInt()}%", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Text("${count(done)} / ${count(total)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // The top card keeps landing on the stack: "newest goes on top".
        val transition = rememberInfiniteTransition(label = "stack")
        val drop by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "drop",
        )
        val landed = (drop / 0.3f).coerceAtMost(1f)
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .graphicsLayer {
                        translationY = (1 - landed) * 18.dp.toPx()
                        alpha = landed
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentGradient),
            )
            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp)).background(Faded))
            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp)).background(Fainter))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (undoing) R.string.working_undo_title else R.string.working_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            if (currentMs != null) {
                val pattern = stringResource(R.string.working_period_pattern)
                val period = remember(currentMs, pattern) {
                    DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(currentMs).atZone(ZoneId.systemDefault()))
                }
                Text(stringResource(R.string.working_period, period), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.working_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FinishedScreen(moved: Int, failed: Int, cancelled: Boolean, keptOriginals: Boolean, onDone: () -> Unit) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f)) }
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!cancelled) {
            Box(
                Modifier
                    .size(120.dp)
                    .scale(pop.value)
                    .clip(CircleShape)
                    .background(AccentGradient),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(60.dp)) {
                    val w = size.width
                    val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(w * 0.25f, w * 0.52f)
                        lineTo(w * 0.42f, w * 0.68f)
                        lineTo(w * 0.75f, w * 0.32f)
                    }
                    drawPath(path, Color.White, style = stroke)
                }
            }
            Text(
                stringResource(R.string.finished_title),
                fontSize = 26.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            if (cancelled) stringResource(R.string.finished_cancelled, moved)
            else stringResource(R.string.finished_body, moved),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(count(moved), stringResource(R.string.stat_resaved), highlight = true)
            Stat(count(failed), stringResource(R.string.stat_failed))
        }
        if (failed > 0) Text(stringResource(R.string.finished_failed, failed))
        if (keptOriginals) Notice(stringResource(R.string.finished_kept))
        Card(colors = cardColors(), elevation = cardElevation()) {
            Text(
                stringResource(R.string.finished_hint),
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GradientButton(stringResource(R.string.action_again), onClick = onDone)
    }
}

/** Shown on the first screen while the last fix can still be undone. */
@Composable
fun UndoCard(log: UndoLog, onUndo: () -> Unit) {
    val days = remember(log) { log.daysLeft(System.currentTimeMillis()) }
    Card(colors = cardColors(), elevation = cardElevation()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.undo_available, count(log.pairs.size), days), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.undo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(shape = outlinedShape, onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_undo))
            }
        }
    }
}

/** Choice between trashing originals (undo for 30 days) and deleting them now (frees space at once). */
@Composable
fun KeepOriginalsCard(keep: Boolean, canKeep: Boolean, extraBytes: Long, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(colors = cardColors(), elevation = cardElevation()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.keep_originals), fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !canKeep -> stringResource(R.string.keep_originals_no_room)
                        keep -> stringResource(R.string.keep_originals_on, Formatter.formatShortFileSize(context, extraBytes))
                        else -> stringResource(R.string.keep_originals_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = keep, onCheckedChange = onChange, enabled = canKeep, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
fun UndoneScreen(restored: Int, missing: Int, cancelled: Boolean, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (cancelled) stringResource(R.string.undo_cancelled) else stringResource(R.string.undo_done, count(restored)),
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (missing > 0) Text(stringResource(R.string.undo_missing, count(missing)))
        if (!cancelled) Notice(stringResource(R.string.undo_done_hint))
        GradientButton(stringResource(R.string.action_again), onClick = onDone)
    }
}
