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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated start screen: logo with a pulsing glow, the app name, and a typewriter line
 * ("Designed by Mr.PC") under the logo.
 */
@Composable
fun SplashScreen(versionName: String, onFinished: () -> Unit) {
    val designed = stringResource(R.string.splash_designed_by)
    val appName = stringResource(R.string.app_name)
    val version = stringResource(R.string.splash_version, versionName)

    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val tagAlpha = remember { Animatable(0f) }
    val typed = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(600)) }
        launch {
            logoScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        launch {
            delay(500)
            nameAlpha.animateTo(1f, tween(600))
        }
        launch {
            delay(1000)
            tagAlpha.animateTo(1f, tween(300))
            typed.animateTo(
                designed.length.toFloat(),
                tween(designed.length * 60, easing = LinearEasing)
            )
        }
        delay(2900)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val shimmer by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer"
    )

    val shownChars = typed.value.toInt().coerceIn(0, designed.length)
    val progress = if (designed.isEmpty()) 0f else shownChars.toFloat() / designed.length.toFloat()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF171C45)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(280.dp)) {
                    val half = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(BrandViolet.copy(alpha = 0.35f * logoAlpha.value), Color.Transparent),
                            center = center,
                            radius = half
                        ),
                        radius = half,
                        center = center
                    )
                    for (k in 0..1) {
                        val p = (pulse + k * 0.5f) % 1f
                        val r = half * (0.45f + 0.55f * p)
                        drawCircle(
                            color = BrandBlue.copy(alpha = (1f - p) * 0.4f * logoAlpha.value),
                            radius = r,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                LogoMark(
                    size = 112.dp,
                    modifier = Modifier
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                )
            }

            Spacer(Modifier.height(4.dp))
            AppText(
                appName,
                modifier = Modifier.alpha(nameAlpha.value),
                size = 30.sp,
                weight = FontWeight.Bold,
                color = Color.White,
                align = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            AppText(
                designed.take(shownChars),
                modifier = Modifier
                    .height(24.dp)
                    .offset(y = ((1f - tagAlpha.value) * 12f).dp)
                    .alpha(tagAlpha.value * shimmer),
                size = 16.sp,
                weight = FontWeight.Medium,
                color = Color(0xFFB9C4FF),
                align = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width((72f * progress).dp)
                    .height(3.dp)
                    .background(BrandGradient)
            )
        }

        AppText(
            version,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .alpha(nameAlpha.value * 0.8f),
            size = 12.sp,
            color = Color(0xFF8E9BC8)
        )
    }
}
