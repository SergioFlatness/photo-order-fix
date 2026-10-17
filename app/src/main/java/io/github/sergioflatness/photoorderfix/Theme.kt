package io.github.sergioflatness.photoorderfix

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design "G2": blue to violet. The violet end is darkened from #8B5CF6 so white text keeps 4.5:1 contrast.
val Blue = Color(0xFF2F5BEA)
val Violet = Color(0xFF7C4DEB)
val AccentGradient = Brush.linearGradient(listOf(Blue, Violet))

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EDFF),
    onPrimaryContainer = Color(0xFF1D44C8),
    secondary = Violet,
    onSecondary = Color.White,
    background = Color(0xFFF5F6FF),
    onBackground = Color(0xFF111B3A),
    surface = Color.White,
    onSurface = Color(0xFF111B3A),
    surfaceVariant = Color(0xFFEFEAFF),
    onSurfaceVariant = Color(0xFF39446A),
    outline = Color(0xFFB9C9FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0C1B55),
    primaryContainer = Color(0xFF26306A),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFC4B1FF),
    background = Color(0xFF0F1124),
    onBackground = Color(0xFFE6E8F7),
    surface = Color(0xFF1A1D38),
    onSurface = Color(0xFFE6E8F7),
    surfaceVariant = Color(0xFF262A4C),
    onSurfaceVariant = Color(0xFFB8BEDB),
    outline = Color(0xFF4C5590),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

/** Faint wash at the top of the screen, as in the mockup. */
@Composable
fun backgroundBrush(): Brush {
    val dark = isSystemInDarkTheme()
    return Brush.verticalGradient(
        0f to if (dark) Color(0xFF1E1A48) else Color(0xFFECE9FF),
        0.45f to MaterialTheme.colorScheme.background,
    )
}

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
fun cardElevation() = CardDefaults.cardElevation(defaultElevation = 1.dp)

/** The main call to action: white text on the blue-to-violet gradient. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentGradient)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}


val outlinedShape = RoundedCornerShape(16.dp)
