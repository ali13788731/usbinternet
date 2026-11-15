package app.usbnet

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.util.Locale

// =================================================================================================
// Theme

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CE1B0),
    secondary = Color(0xFF7C9CFF),
    background = Color(0xFF0B1020),
    surface = Color(0xFF151D38),
    surfaceVariant = Color(0xFF1C2646),
    onBackground = Color(0xFFEAF0FF),
    onSurface = Color(0xFFEAF0FF),
    onSurfaceVariant = Color(0xFF9AA8D0),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0FA678),
    secondary = Color(0xFF3E63E8),
    background = Color(0xFFF2F5FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE3E9FB),
    onBackground = Color(0xFF111A33),
    onSurface = Color(0xFF111A33),
    onSurfaceVariant = Color(0xFF55618A),
    error = Color(0xFFD64545),
)

@Composable
fun UsbNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

private fun stateColor(state: LinkState): Color = when (state) {
    LinkState.Idle -> Color(0xFF7C8AB8)
    LinkState.Starting -> Color(0xFF7C9CFF)
    LinkState.WaitingForPc -> Color(0xFFFFB84D)
    LinkState.Connected -> Color(0xFF2DD4A0)
}

// =================================================================================================
// Screen

@Composable
fun MainScreen(onToggleLanguage: () -> Unit) {
    val ctx = LocalContext.current
    val snap by TunnelMonitor.snapshot.collectAsState()
    val haptic = LocalHapticFeedback.current

    var dnsChoice by remember { mutableIntStateOf(Prefs.dnsChoice(ctx)) }
    var customDns by remember { mutableStateOf(Prefs.customDns(ctx)) }

    // Notification permission (Android 13+). Only used for the status notification.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val startTunnel: () -> Unit = { TunnelService.start(ctx, Prefs.resolveDns(ctx)) }
    val failedText = stringResource(R.string.vpn_failed)
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startTunnel()
        } else {
            Toast.makeText(ctx, failedText, Toast.LENGTH_SHORT).show()
        }
    }

    val onToggle: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (snap.state == LinkState.Idle) {
            val prepare = VpnService.prepare(ctx)
            if (prepare != null) vpnLauncher.launch(prepare) else startTunnel()
        } else {
            TunnelService.stop(ctx)
        }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.background, scheme.surfaceVariant)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Reveal(0) { Header(onToggleLanguage) }
            Reveal(1) { Hero(snap.state, onToggle) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Reveal(2, Modifier.weight(1f)) {
                    SpeedCard(
                        label = stringResource(R.string.download),
                        arrow = "↓",
                        bps = snap.downBps,
                        color = scheme.primary
                    )
                }
                Reveal(2, Modifier.weight(1f)) {
                    SpeedCard(
                        label = stringResource(R.string.upload),
                        arrow = "↑",
                        bps = snap.upBps,
                        color = scheme.secondary
                    )
                }
            }

            Reveal(3) { ChartCard(snap) }
            Reveal(4) { StatsCard(snap) }

            AnimatedVisibility(
                visible = snap.state != LinkState.Connected,
                enter = fadeIn(tween(350)) + expandVertically(tween(350)),
                exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
            ) {
                GuideCard()
            }

            Reveal(5) {
                DnsCard(
                    enabled = snap.state == LinkState.Idle,
                    choice = dnsChoice,
                    custom = customDns,
                    onChoice = {
                        dnsChoice = it
                        Prefs.setDnsChoice(ctx, it)
                    },
                    onCustom = {
                        customDns = it
                        Prefs.setCustomDns(ctx, it)
                    }
                )
            }
        }
    }
}

/** Fade + slide in with a small stagger. */
@Composable
private fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 6 }
    ) {
        content()
    }
}

@Composable
private fun Header(onToggleLanguage: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surface.copy(alpha = 0.9f))
                .clickable(onClick = onToggleLanguage)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.lang_switch_label),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
        }
    }
}

// =================================================================================================
// Hero: big animated connect button

@Composable
private fun Hero(state: LinkState, onToggle: () -> Unit) {
    val accent by animateColorAsState(stateColor(state), tween(500), label = "accent")

    val infinite = rememberInfiniteTransition(label = "hero")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (state == LinkState.Connected) 2600 else 1500, easing = LinearEasing)
        ),
        label = "pulse"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f),
        label = "press"
    )

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val half = size.minDimension / 2f
                val base = size.minDimension * 0.34f

                // ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                        center = c,
                        radius = half
                    ),
                    radius = half,
                    center = c
                )

                // expanding pulse rings
                if (state != LinkState.Idle) {
                    for (i in 0..1) {
                        val p = (pulse + i * 0.5f) % 1f
                        drawCircle(
                            color = accent.copy(alpha = (1f - p) * 0.35f),
                            radius = base * (1f + p * 0.45f),
                            center = c,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // rotating arc while starting / waiting for the PC
                if (state == LinkState.Starting || state == LinkState.WaitingForPc) {
                    val r = base + 14.dp.toPx()
                    drawArc(
                        color = accent,
                        startAngle = spin,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Box(
                Modifier
                    .size(176.dp)
                    .scale(pressScale)
                    .shadow(
                        elevation = if (state == LinkState.Idle) 8.dp else 20.dp,
                        shape = CircleShape,
                        ambientColor = accent,
                        spotColor = accent
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(lerp(accent, Color.White, 0.25f), accent))
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                PowerIcon()
            }
        }

        Spacer(Modifier.height(4.dp))

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 })
                    .togetherWith(fadeOut(tween(200)))
            },
            label = "status"
        ) { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(statusTitle(s)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = stateColor(s),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(statusSub(s)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun statusTitle(s: LinkState) = when (s) {
    LinkState.Idle -> R.string.status_idle_title
    LinkState.Starting -> R.string.status_starting_title
    LinkState.WaitingForPc -> R.string.status_waiting_title
    LinkState.Connected -> R.string.status_connected_title
}

private fun statusSub(s: LinkState) = when (s) {
    LinkState.Idle -> R.string.status_idle_sub
    LinkState.Starting -> R.string.status_starting_sub
    LinkState.WaitingForPc -> R.string.status_waiting_sub
    LinkState.Connected -> R.string.status_connected_sub
}

@Composable
private fun PowerIcon() {
    Canvas(Modifier.size(64.dp)) {
        val w = size.minDimension
        val stroke = w * 0.09f
        val r = w * 0.36f
        val cy = center.y + w * 0.04f
        drawArc(
            color = Color.White,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(center.x - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = Color.White,
            start = Offset(center.x, cy - r - w * 0.06f),
            end = Offset(center.x, cy - w * 0.04f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

// =================================================================================================
// Cards

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SpeedCard(label: String, arrow: String, bps: Long, color: Color) {
    val animated by animateFloatAsState(bps.toFloat(), tween(500), label = "speed")
    val (value, unit) = splitSpeed(animated.toLong())
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(arrow, color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun ChartCard(snap: Snapshot) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.live_traffic),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            LegendDot(scheme.primary, stringResource(R.string.download))
            Spacer(Modifier.width(12.dp))
            LegendDot(scheme.secondary, stringResource(R.string.upload))
        }
        Spacer(Modifier.height(12.dp))
        LiveChart(
            down = snap.downHistory,
            up = snap.upHistory,
            downColor = scheme.primary,
            upColor = scheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LiveChart(
    down: List<Long>,
    up: List<Long>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier
) {
    val targetMax = maxOf(
        64L * 1024,
        down.maxOrNull() ?: 0L,
        up.maxOrNull() ?: 0L
    ).toFloat()
    val yMax by animateFloatAsState(targetMax, tween(600), label = "ymax")
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Canvas(modifier) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val n = TunnelMonitor.HISTORY

        // download: filled area + line
        drawPath(
            path = smoothPath(down, n, yMax, close = true),
            brush = Brush.verticalGradient(
                listOf(downColor.copy(alpha = 0.35f), Color.Transparent)
            )
        )
        drawPath(
            path = smoothPath(down, n, yMax, close = false),
            color = downColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // upload: line only
        drawPath(
            path = smoothPath(up, n, yMax, close = false),
            color = upColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Smooth (bezier) line through the last [n] samples; older slots are padded with zeros. */
private fun DrawScope.smoothPath(data: List<Long>, n: Int, yMax: Float, close: Boolean): Path {
    val padded = List(maxOf(0, n - data.size)) { 0L } + data.takeLast(n)
    val stepX = size.width / (n - 1)
    val usable = size.height * 0.92f
    fun px(i: Int) = i * stepX
    fun py(v: Long) = size.height - (v / yMax).coerceIn(0f, 1f) * usable - 2f

    val path = Path()
    path.moveTo(0f, py(padded[0]))
    for (i in 1 until padded.size) {
        val x0 = px(i - 1)
        val y0 = py(padded[i - 1])
        val x1 = px(i)
        val y1 = py(padded[i])
        val cx = (x0 + x1) / 2f
        path.cubicTo(cx, y0, cx, y1, x1, y1)
    }
    if (close) {
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()
    }
    return path
}

@Composable
private fun StatsCard(snap: Snapshot) {
    val elapsedSec = if (snap.connectedSinceMs > 0) {
        ((System.currentTimeMillis() - snap.connectedSinceMs) / 1000).coerceAtLeast(0)
    } else 0L
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.total_received), formatBytes(snap.totalDown))
            Stat(stringResource(R.string.total_sent), formatBytes(snap.totalUp))
            Stat(stringResource(R.string.session_time), formatDuration(elapsedSec))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isUsbDebuggingOn(ctx: android.content.Context): Boolean = try {
    Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
} catch (e: Exception) {
    false
}

/** Opens Developer options, or About phone (to unlock them), or plain Settings as a last resort. */
private fun openDeveloperSettings(ctx: android.content.Context) {
    val attempts = listOf(
        Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        Settings.ACTION_DEVICE_INFO_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in attempts) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
}

/** Shows whether USB debugging is on and gives a one-tap shortcut to the right settings page. */
@Composable
private fun UsbDebugRow() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(isUsbDebuggingOn(ctx)) }
    val owner = ctx as? ComponentActivity
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = isUsbDebuggingOn(ctx)
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    if (enabled) R.string.usbdebug_on else R.string.usbdebug_off
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) scheme.primary else scheme.error
            )
            if (!enabled) {
                Text(
                    stringResource(R.string.usbdebug_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (!enabled) {
            Spacer(Modifier.width(10.dp))
            Pill(stringResource(R.string.usbdebug_open), false, true) { openDeveloperSettings(ctx) }
        }
    }
}

@Composable
private fun GuideCard() {
    val steps = listOf(
        R.string.guide_step1,
        R.string.guide_step2,
        R.string.guide_step3,
        R.string.guide_step4
    )
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        UsbDebugRow()
        Spacer(Modifier.height(6.dp))
        steps.forEachIndexed { index, res ->
            Row(
                Modifier.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        String.format(Locale.US, "%d", index + 1),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DnsCard(
    enabled: Boolean,
    choice: Int,
    custom: String,
    onChoice: (Int) -> Unit,
    onCustom: (String) -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.dns_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill(stringResource(R.string.dns_google), choice == Prefs.DNS_GOOGLE, enabled) {
                onChoice(Prefs.DNS_GOOGLE)
            }
            Pill(stringResource(R.string.dns_cloudflare), choice == Prefs.DNS_CLOUDFLARE, enabled) {
                onChoice(Prefs.DNS_CLOUDFLARE)
            }
            Pill(stringResource(R.string.dns_custom), choice == Prefs.DNS_CUSTOM, enabled) {
                onChoice(Prefs.DNS_CUSTOM)
            }
        }
        AnimatedVisibility(
            visible = choice == Prefs.DNS_CUSTOM,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                val invalid = custom.isNotBlank() && !Prefs.isValidIpv4(custom)
                OutlinedTextField(
                    value = custom,
                    onValueChange = onCustom,
                    enabled = enabled,
                    singleLine = true,
                    isError = invalid,
                    label = { Text(stringResource(R.string.dns_custom_hint)) },
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.dns_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (!enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dns_locked_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.2f) else scheme.surfaceVariant,
        tween(250),
        label = "pill"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                1.dp,
                if (selected) scheme.primary else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// =================================================================================================
// Formatting

private fun splitSpeed(bps: Long): Pair<String, String> = when {
    bps < 1024 -> String.format(Locale.US, "%d", bps) to "B/s"
    bps < 1024 * 1024 -> String.format(Locale.US, "%.1f", bps / 1024.0) to "KB/s"
    else -> String.format(Locale.US, "%.2f", bps / 1048576.0) to "MB/s"
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> String.format(Locale.US, "%d B", b)
    b < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
