package io.github.sergioflatness.photoorderfix

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Header(version: String?, onInfo: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGradient),
        ) {
            // The launcher foreground has the adaptive-icon safe zone built in, so it can fill the tile.
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(44.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(stringResource(R.string.app_name), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (version != null) {
                Text("v$version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onInfo != null) InfoButton(onInfo)
    }
}

@Composable
fun IntroScreen(onScan: () -> Unit) {
    Card(colors = cardColors(), elevation = cardElevation(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrderPicture()
            Text(
                stringResource(R.string.intro_headline),
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(stringResource(R.string.intro_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.intro_step1, R.string.intro_step2, R.string.intro_step3).forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(stringResource(step), Modifier.padding(start = 12.dp))
            }
        }
    }
    Text(
        stringResource(R.string.intro_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        textAlign = TextAlign.Center,
    )
    GradientButton(stringResource(R.string.action_scan), onClick = onScan)
}

/** Jumbled stack on the left, sorted stack (newest on top) on the right. */
@Composable
private fun OrderPicture() {
    val light = Color(0xFFB9C9FF)
    val mid = Color(0xFF8EA8FF)
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE6EDFF), Color(0xFFEFE8FF)))),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stack(listOf(SolidColor(light), SolidColor(mid), AccentGradient))
        Text("→", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111B3A), modifier = Modifier.padding(horizontal = 18.dp))
        Stack(listOf(AccentGradient, SolidColor(mid), SolidColor(light)))
    }
}

@Composable
private fun Stack(fills: List<Brush>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fills.forEach { fill ->
            Box(
                Modifier
                    .width(64.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fill),
            )
        }
    }
}
