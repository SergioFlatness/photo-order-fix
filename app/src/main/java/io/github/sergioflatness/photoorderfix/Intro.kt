package io.github.sergioflatness.photoorderfix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.sergioflatness.photoorderfix.core.UndoLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** First screen: what the app does, then "check my photos". After a fix it offers undo instead of the steps. */
@Composable
fun HomeScreen(undo: UndoLog?, onUndo: () -> Unit, onHelp: () -> Unit, onScan: () -> Unit) {
    Page(bottom = { GradientButton(stringResource(R.string.action_scan), onClick = onScan) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(role = Role.Button, onClick = onHelp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.help_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Headline(stringResource(R.string.intro_headline), size = 34)
            Text(stringResource(R.string.intro_body), fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppCard(padding = 20.dp) {
            // Captions live inside the drawing so they stay under their photos when the card is wider (unfolded Fold).
            FitBox(316.dp, 236.dp) {
                Box {
                    OrderIllustration()
                    Caption(stringResource(R.string.intro_before), centerX = 67, FontWeight.Medium, MaterialTheme.colorScheme.onSurfaceVariant)
                    Caption(stringResource(R.string.intro_after), centerX = 258, FontWeight.Bold, MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (undo != null) {
            UndoBanner(undo, onUndo)
        } else {
            AppCard(padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(R.string.intro_step1, R.string.intro_step2, R.string.intro_step3).forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text(stringResource(step), Modifier.padding(start = 14.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** "Last time you fixed 176 photos · N days left to undo". */
@Composable
private fun UndoBanner(log: UndoLog, onUndo: () -> Unit) {
    val days = remember(log) { log.daysLeft(System.currentTimeMillis()) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NoteShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.undo_last, count(log.pairs.size)), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.undo_days_left, days), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.action_undo), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** A label centered under one photo group of [OrderIllustration], in its 316 x 236 coordinates. */
@Composable
private fun Caption(text: String, centerX: Int, weight: FontWeight, color: Color) {
    Box(Modifier.offset((centerX - 80).dp, 214.dp).width(160.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, fontSize = 12.sp, fontWeight = weight, color = color, maxLines = 1, softWrap = false)
    }
}

/** Jumbled photos, an arrow, and the same photos stacked in the order they were taken. Floats gently. */
@Composable
fun OrderIllustration() {
    Box(Modifier.size(316.dp, 210.dp).floating()) {
        // Centers and angles from the Figma frame "Signature: 뒤죽박죽 → 찍은 순서".
        listOf(
            Triple("2025", 2, Triple(30.5f, 97.5f, -14f)),
            Triple("2026", 3, Triple(100.5f, 134f, 9f)),
            Triple("2023", 0, Triple(103f, 47f, 7f)),
            Triple("2024", 1, Triple(41f, 158.5f, -4f)),
        ).forEach { (label, shade, at) ->
            PhotoTile(label, shade, Modifier.offset((at.first - 36).dp, (at.second - 44).dp), rotation = at.third)
        }
        Icon(LineIcons.Back, null, Modifier.offset(152.dp, 82.dp).size(36.dp).graphicsLayer { rotationZ = 180f }, tint = MaterialTheme.colorScheme.primary)
        listOf("2023", "2024", "2025", "2026").forEachIndexed { i, label ->
            PhotoTile(label, i, Modifier.offset(222.dp, (4 + 34 * i).dp))
        }
    }
}

/** Shown while the scan runs. */
@Composable
fun ScanningScreen() {
    Page {
        VSpace(60.dp)
        FitBox(316.dp, 210.dp) { OrderIllustration() }
        Text(
            stringResource(R.string.scanning),
            Modifier.fillMaxWidth(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Shown once, before the first scan: back up first, originals stay in the trash, the app comes as is. */
@Composable
fun NoticeScreen(onAccept: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val badge = remember { Animatable(0f) }
    val rows = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        launch { badge.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
        rows.forEachIndexed { i, a -> launch { delay(150L + 120L * i); a.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) } }
    }
    Page(bottom = {
        Text(
            stringResource(R.string.notice_terms),
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { open(context, TERMS_URL) }.padding(8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        GradientButton(stringResource(R.string.notice_accept), onClick = { confirm = true })
    }) {
        VSpace(24.dp)
        Box(
            Modifier
                .size(70.dp)
                .graphicsLayer { scaleX = badge.value; scaleY = badge.value; rotationZ = -30f * (1 - badge.value) }
                .clip(CookieShape)
                .background(AccentGradient),
            contentAlignment = Alignment.Center,
        ) { Text("!", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black) }
        Headline(stringResource(R.string.notice_title))
        AppCard(padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(
                    Triple(LineIcons.Backup, R.string.notice_backup_title, R.string.notice_backup),
                    Triple(LineIcons.Trash, R.string.notice_trash_title, R.string.notice_trash),
                    Triple(LineIcons.Info, R.string.notice_free_title, R.string.notice_free),
                ).forEachIndexed { i, (icon, title, body) ->
                    NoticeRow(icon, stringResource(title), stringResource(body), Modifier.graphicsLayer {
                        alpha = rows[i].value
                        translationY = (1 - rows[i].value) * 14.dp.toPx()
                    })
                }
            }
        }
    }
    if (confirm) ConfirmDialog(onBack = { confirm = false }, onStart = onAccept)
}

@Composable
private fun NoticeRow(icon: ImageVector, title: String, body: String, modifier: Modifier) {
    Row(modifier) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Really start?" with a checkbox, so the notice is read and not just tapped away. */
@Composable
private fun ConfirmDialog(onBack: () -> Unit, onStart: () -> Unit) {
    var read by remember { mutableStateOf(false) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Dialog(onDismissRequest = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = pop.value; translationY = (1 - pop.value) * 40.dp.toPx(); scaleX = 0.92f + 0.08f * pop.value; scaleY = scaleX }
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(stringResource(R.string.confirm_title), fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                stringResource(R.string.confirm_body),
                Modifier.padding(top = 12.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(role = Role.Checkbox) { read = !read }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (read) Icon(LineIcons.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Text(
                    stringResource(R.string.confirm_check),
                    Modifier.padding(start = 10.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(stringResource(R.string.confirm_reread), onClick = onBack, modifier = Modifier.weight(1f), height = 48.dp)
                GradientButton(stringResource(R.string.confirm_start), onClick = onStart, modifier = Modifier.weight(1f), enabled = read, height = 48.dp)
            }
        }
    }
}
