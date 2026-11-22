package app.usbnet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Two stages: (1) only the logo with a pulsing glow, (2) the logo fades away and an animated scene
 * (phone - cable - computer with packets flowing) appears with the app name and, once in the whole app,
 * the "Designed by" credit.
 */
@Composable
fun SplashScreen(versionName: String, onFinished: () -> Unit) {
    val designed = stringResource(R.string.splash_designed_by)
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.app_tagline)
    val version = stringResource(R.string.splash_version, versionName)

    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val stage2 = remember { Animatable(0f) }
    val typed = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(500)) }
        launch {
            logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        delay(1500)
        // stage 2: logo disappears, scene appears
        launch { logoAlpha.animateTo(0f, tween(450)) }
        launch { stage2.animateTo(1f, tween(700)) }
        delay(900)
        typed.animateTo(designed.length.toFloat(), tween(designed.length * 55 + 1, easing = LinearEasing))
        delay(900)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val flow by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "flow"
    )

    val shownChars = typed.value.toInt().coerceIn(0, designed.length)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF171C45)))),
        contentAlignment = Alignment.Center
    ) {
        // Stage 1: logo
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(280.dp)) {
                val half = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(BrandViolet.copy(alpha = 0.35f * logoAlpha.value), Color.Transparent),
                        center = center, radius = half
                    ),
                    radius = half, center = center
                )
                for (k in 0..1) {
                    val p = (pulse + k * 0.5f) % 1f
                    drawCircle(
                        color = BrandBlue.copy(alpha = (1f - p) * 0.4f * logoAlpha.value),
                        radius = half * (0.45f + 0.55f * p), center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            LogoMark(size = 112.dp, modifier = Modifier.scale(logoScale.value).alpha(logoAlpha.value))
        }

        // Stage 2: animated scene, no logo
        Column(
            Modifier.alpha(stage2.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.width(280.dp).height(120.dp)) {
                val y = size.height / 2f
                val left = 44.dp.toPx()
                val right = size.width - 44.dp.toPx()
                // cable
                drawLine(Color(0xFF3A4680), Offset(left, y), Offset(right, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                // packets
                for (k in 0..3) {
                    val p = (flow + k * 0.25f) % 1f
                    val x = left + (right - left) * p
                    val a = (1f - kotlin.math.abs(p - 0.5f) * 1.2f).coerceIn(0.15f, 1f)
                    drawCircle(BrandBlue.copy(alpha = a), radius = 5.dp.toPx(), center = Offset(x, y))
                }
                // phone
                drawRoundRect(
                    BrandViolet, topLeft = Offset(left - 30.dp.toPx(), y - 30.dp.toPx()),
                    size = Size(26.dp.toPx(), 60.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
                // computer
                drawRoundRect(
                    BrandBlue, topLeft = Offset(right + 6.dp.toPx(), y - 24.dp.toPx()),
                    size = Size(40.dp.toPx(), 30.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawLine(BrandBlue, Offset(right + 26.dp.toPx(), y + 6.dp.toPx()), Offset(right + 26.dp.toPx(), y + 16.dp.toPx()), strokeWidth = 3.dp.toPx())
                drawLine(BrandBlue, Offset(right + 14.dp.toPx(), y + 17.dp.toPx()), Offset(right + 38.dp.toPx(), y + 17.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            Spacer(Modifier.height(10.dp))
            AppText(appName, size = 32.sp, weight = FontWeight.Bold, color = Color.White, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            AppText(tagline, size = 14.sp, color = Color(0xFF8E9BC8), align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            AppText(
                designed.take(shownChars),
                modifier = Modifier.height(24.dp),
                size = 16.sp, weight = FontWeight.Medium, color = Color(0xFFB9C4FF), align = TextAlign.Center
            )
        }

        AppText(
            version,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .alpha(stage2.value * 0.8f),
            size = 12.sp, color = Color(0xFF8E9BC8)
        )
    }
}
