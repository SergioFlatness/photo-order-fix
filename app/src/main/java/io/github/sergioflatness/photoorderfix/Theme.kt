package io.github.sergioflatness.photoorderfix

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Design "G2": blue to violet. The violet end is darkened from #8B5CF6 so white text keeps 4.5:1 contrast.
val Blue = Color(0xFF2F5BEA)
val Violet = Color(0xFF7C4DEB)
val AccentGradient = Brush.linearGradient(listOf(Blue, Violet))

/** Photo tiles go from light to dark blue in the order the photos were taken. */
val TileRamp = listOf(Color(0xFFDCE8FF), Color(0xFFA9C4FF), Color(0xFF6F98F5), Color(0xFF3F6AE0))
private val TileRampEnd = listOf(Color(0xFFECF2FF), Color(0xFFD0DFFF), Color(0xFFB0C6FA), Color(0xFF95ADEE))

/** Color for step [i] of [n] on the light-to-dark blue ramp (the per-year bars). */
fun rampColor(i: Int, n: Int): Color =
    if (n <= 1) Color(0xFF6F98F5) else lerp(Color(0xFFA9C4FF), Color(0xFF3F6AE0), i / (n - 1f))

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EDFF),
    onPrimaryContainer = Color(0xFF2448C8),
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1EEFF),
    background = Color(0xFFF5F6FF),
    onBackground = Color(0xFF111B3A),
    surface = Color.White,
    onSurface = Color(0xFF111B3A),
    surfaceVariant = Color(0xFFE6EDFF),
    onSurfaceVariant = Color(0xFF39446A),
    outline = Color(0xFFB9C9FF),
    outlineVariant = Color(0xFFE6EAF5),
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFFB71C1C),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0C1B55),
    primaryContainer = Color(0xFF26306A),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFC4B1FF),
    secondaryContainer = Color(0xFF2A2650),
    background = Color(0xFF0F1124),
    onBackground = Color(0xFFE6E8F7),
    surface = Color(0xFF1A1D38),
    onSurface = Color(0xFFE6E8F7),
    surfaceVariant = Color(0xFF262A4C),
    onSurfaceVariant = Color(0xFFB8BEDB),
    outline = Color(0xFF4C5590),
    outlineVariant = Color(0xFF2E3358),
    errorContainer = Color(0xFF4A1C22),
    onErrorContainer = Color(0xFFFFB4AB),
    error = Color(0xFFFF8A80),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

/** Faint wash at the top of the screen, as in the mockup. */
@Composable
fun backgroundBrush(): Brush {
    val dark = isSystemInDarkTheme()
    return Brush.verticalGradient(
        0f to if (dark) Color(0xFF1E1A48) else Color(0xFFF1EEFF),
        0.45f to MaterialTheme.colorScheme.background,
    )
}

val CardShape = RoundedCornerShape(28.dp)
val NoteShape = RoundedCornerShape(24.dp)

/** One screen: [content] scrolls, [bottom] (the main buttons) stays at the bottom. */
@Composable
fun Page(bottom: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

/** White rounded card. */
@Composable
fun AppCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(padding),
        content = content,
    )
}

/** Tinted card for advice, with a blue title. */
@Composable
fun NoteCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(NoteShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        content()
    }
}

/** A line in a [NoteCard]: small icon, then text. */
@Composable
fun NoteLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text, Modifier.padding(start = 10.dp), fontSize = 14.sp, lineHeight = 21.sp)
    }
}

/** Big screen title. */
@Composable
fun Headline(text: String, size: Int = 30) {
    Text(text, fontSize = size.sp, lineHeight = (size * 1.3f).sp, fontWeight = FontWeight.Black)
}

/** Round back button and the screen name, at the top of the screens after the first. */
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    val back = stringResource(R.string.action_back)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button, onClickLabel = back, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LineIcons.Back, back, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(title, Modifier.padding(start = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** The main call to action: white text on the blue-to-violet gradient, as a pill. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 60.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(height / 2))
            .background(AccentGradient)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = if (height < 56.dp) 15.sp else 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Secondary pill button on a light blue fill. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = if (height < 56.dp) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small grey line under the main button. */
@Composable
fun Footnote(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Soft 9-point "cookie" badge shape, as in Material 3 Expressive. */
val CookieShape = GenericShape { size, _ ->
    val cx = size.width / 2
    val cy = size.height / 2
    val r = min(cx, cy) / 1.082f
    val steps = 180
    for (i in 0..steps) {
        val a = i * 2 * PI / steps
        val radius = r * (1 + 0.082f * cos(9 * a)).toFloat()
        val x = cx + radius * cos(a - PI / 2).toFloat()
        val y = cy + radius * sin(a - PI / 2).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A photo card: blue gradient by its place in time, with a small year label. */
@Composable
fun PhotoTile(label: String, shade: Int, modifier: Modifier = Modifier, width: Dp = 72.dp, height: Dp = 88.dp, rotation: Float = 0f) {
    val i = shade.coerceIn(0, TileRamp.lastIndex)
    Box(
        modifier
            .size(width, height)
            .rotate(rotation)
            .shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x24121A3B), spotColor = Color(0x24121A3B))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(TileRamp[i], TileRampEnd[i]))),
    ) {
        Box(
            Modifier
                .padding(7.dp)
                .width(38.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111B3A))
        }
    }
}

/** Gentle up-and-down float (3dp), shared by the photo illustrations. */
fun Modifier.floating(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    graphicsLayer { translationY = v * 3.dp.toPx() }
}

/**
 * Draws [content] at its design size ([width] x [height]) and shrinks it to fit narrow screens,
 * so drawn-in-place illustrations keep their layout.
 */
@Composable
fun FitBox(width: Dp, height: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val s = min(1f, maxWidth / width)
        Box(Modifier.size(width * s, height * s), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(width, height).graphicsLayer { scaleX = s; scaleY = s }) { content() }
        }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
