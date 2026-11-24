package app.usbnet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * Single animated scene (phone - cable - computer with packets flowing) with the app name,
 * the tagline and the "Designed by" credit.
 */
@Composable
fun SplashScreen(versionName: String, onFinished: () -> Unit) {
    val designed = stringResource(R.string.splash_designed_by)
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.app_tagline)
    val version = stringResource(R.string.splash_version, versionName)

    val stage2 = remember { Animatable(0f) }
    val typed = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Only the animated connection scene: it fades in at once, then the credit is typed out.
        launch { stage2.animateTo(1f, tween(500)) }
        delay(600)
        typed.animateTo(designed.length.toFloat(), tween(designed.length * 55 + 1, easing = LinearEasing))
        delay(900)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val flow by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "flow"
    )

    val shownChars = typed.value.toInt().coerceIn(0, designed.length)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1110), Color(0xFF0F2B28)))),
        contentAlignment = Alignment.Center
    ) {
        // Animated scene: phone - cable - computer with packets flowing
        Column(
            Modifier.alpha(stage2.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.width(280.dp).height(120.dp)) {
                val y = size.height / 2f
                val left = 44.dp.toPx()
                val right = size.width - 44.dp.toPx()
                // cable
                drawLine(Color(0xFF2E5B55), Offset(left, y), Offset(right, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
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
            AppText(tagline, size = 14.sp, color = Color(0xFF8FB5AF), align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            AppText(
                designed.take(shownChars),
                modifier = Modifier.height(24.dp),
                size = 16.sp, weight = FontWeight.Medium, color = Color(0xFFB4E6DE), align = TextAlign.Center
            )
        }

        AppText(
            version,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .alpha(stage2.value * 0.8f),
            size = 12.sp, color = Color(0xFF8FB5AF)
        )
    }
}
