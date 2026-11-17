package app.usbnet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    snap: Snapshot,
    settings: SettingsState,
    onToggle: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val connected = snap.state == LinkState.Connected

    val toggle: () -> Unit = {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }

    // Live clock for the session duration.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snap.connectedSinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val chipColor = when (snap.state) {
        LinkState.Idle -> c.muted
        LinkState.Starting -> BrandBlue
        LinkState.WaitingForPc -> AccentAmber
        LinkState.Connected -> AccentGreen
    }
    val chipText = stringResource(
        when (snap.state) {
            LinkState.Idle -> R.string.chip_idle
            LinkState.Starting -> R.string.chip_starting
            LinkState.WaitingForPc -> R.string.chip_waiting
            LinkState.Connected -> R.string.chip_connected
        }
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Reveal(0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LogoMark(size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AppText(stringResource(R.string.app_name), size = 20.sp, weight = FontWeight.Bold, maxLines = 1)
                    AppText(stringResource(R.string.app_tagline), size = 12.sp, color = c.muted, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(chipText, chipColor)
            }
        }

        Reveal(1) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ConnectButton(snap.state, toggle)
                Spacer(Modifier.height(6.dp))
                AnimatedContent(
                    targetState = snap.state,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "statusText"
                ) { st ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(
                            stringResource(
                                when (st) {
                                    LinkState.Idle -> R.string.status_idle_title
                                    LinkState.Starting -> R.string.status_starting_title
                                    LinkState.WaitingForPc -> R.string.status_waiting_title
                                    LinkState.Connected -> R.string.status_connected_title
                                }
                            ),
                            size = 24.sp,
                            weight = FontWeight.Bold,
                            align = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        AppText(
                            stringResource(
                                when (st) {
                                    LinkState.Idle -> R.string.status_idle_sub
                                    LinkState.Starting -> R.string.status_starting_sub
                                    LinkState.WaitingForPc -> R.string.status_waiting_sub
                                    LinkState.Connected -> R.string.status_connected_sub
                                }
                            ),
                            size = 14.sp,
                            color = c.muted,
                            align = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (snap.state) {
                    LinkState.Idle -> PrimaryButton(
                        text = stringResource(R.string.btn_connect),
                        onClick = toggle,
                        modifier = Modifier.width(240.dp)
                    )
                    LinkState.Connected -> GhostButton(
                        text = stringResource(R.string.btn_disconnect),
                        onClick = toggle,
                        modifier = Modifier.width(240.dp),
                        color = AccentRed
                    )
                    else -> GhostButton(
                        text = stringResource(R.string.btn_cancel_connect),
                        onClick = toggle,
                        modifier = Modifier.width(240.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = connected,
            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpeedCard(
                        modifier = Modifier.weight(1f),
                        ic = Ic.Down,
                        label = stringResource(R.string.download),
                        value = formatSpeed(snap.downBps, settings.speedUnit),
                        color = BrandBlue
                    )
                    SpeedCard(
                        modifier = Modifier.weight(1f),
                        ic = Ic.Up,
                        label = stringResource(R.string.upload),
                        value = formatSpeed(snap.upBps, settings.speedUnit),
                        color = BrandViolet
                    )
                }
                if (settings.showGraph) {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(Ic.Pulse, c.muted, size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            AppText(
                                stringResource(R.string.live_traffic),
                                modifier = Modifier.weight(1f),
                                size = 15.sp,
                                weight = FontWeight.SemiBold
                            )
                            LegendDot(BrandBlue)
                            Spacer(Modifier.width(12.dp))
                            LegendDot(BrandViolet)
                        }
                        Spacer(Modifier.height(12.dp))
                        TrafficChart(snap.downHistory, snap.upHistory)
                    }
                }
                AppCard {
                    val seconds = if (snap.connectedSinceMs > 0L) {
                        ((now - snap.connectedSinceMs) / 1000L).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(Modifier.weight(1f), stringResource(R.string.total_received), formatBytes(snap.totalDown))
                        StatCell(Modifier.weight(1f), stringResource(R.string.total_sent), formatBytes(snap.totalUp))
                        StatCell(Modifier.weight(1f), stringResource(R.string.session_time), formatDuration(seconds))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !connected,
            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppCard {
                    AppText(
                        stringResource(R.string.home_checklist_title),
                        size = 16.sp,
                        weight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    StepItem(1, stringResource(R.string.guide_step1))
                    StepItem(2, stringResource(R.string.guide_step2))
                    StepItem(3, stringResource(R.string.guide_step3))
                    StepItem(4, stringResource(R.string.guide_step4))
                }
                AppCard {
                    UsbDebugRow()
                }
                GhostButton(
                    text = stringResource(R.string.home_open_guide),
                    onClick = onOpenGuide,
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandBlue
                )
            }
        }
    }
}

@Composable
private fun ConnectButton(state: LinkState, onClick: () -> Unit) {
    val accent by animateColorAsState(
        targetValue = when (state) {
            LinkState.Idle -> BrandBlue
            LinkState.Starting -> BrandBlue
            LinkState.WaitingForPc -> AccentAmber
            LinkState.Connected -> AccentGreen
        },
        animationSpec = tween(500),
        label = "connectAccent"
    )
    val infinite = rememberInfiniteTransition(label = "connect")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "spin"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val working = state == LinkState.Starting || state == LinkState.WaitingForPc

    Box(
        Modifier
            .size(240.dp)
            .clip(CircleShape)
            .bounceClick(true, onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension / 2f
            val glow = when (state) {
                LinkState.Connected -> 0.20f + 0.14f * breathe
                LinkState.Idle -> 0.10f + 0.07f * breathe
                else -> 0.18f
            }
            drawCircle(accent.copy(alpha = glow), radius = base * (0.88f + 0.12f * breathe))
            drawCircle(
                accent.copy(alpha = 0.30f),
                radius = base * 0.74f,
                style = Stroke(width = 3.dp.toPx())
            )
            if (working) {
                val r = base * 0.74f
                rotate(spin) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            drawCircle(
                brush = Brush.linearGradient(listOf(accent, lerp(accent, BrandViolet, 0.55f))),
                radius = base * 0.6f
            )
        }
        AppIcon(
            when (state) {
                LinkState.Idle -> Ic.Power
                LinkState.Connected -> Ic.Check
                else -> Ic.Pulse
            },
            Color.White,
            size = 64.dp
        )
    }
}

@Composable
private fun SpeedCard(modifier: Modifier, ic: Ic, label: String, value: String, color: Color) {
    val c = LocalAppColors.current
    AppCard(modifier = modifier, padding = 16.dp) {
        IconBadge(ic, color, size = 38.dp)
        Spacer(Modifier.height(12.dp))
        AppText(label, size = 12.sp, color = c.muted)
        Spacer(Modifier.height(2.dp))
        AppText(value, size = 18.sp, weight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatCell(modifier: Modifier, label: String, value: String) {
    val c = LocalAppColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(value, size = 16.sp, weight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        AppText(label, size = 12.sp, color = c.muted, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun TrafficChart(down: List<Long>, up: List<Long>) {
    val c = LocalAppColors.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val points = TunnelMonitor.HISTORY
        val maxValue = maxOf(1L, maxOf(down.maxOrNull() ?: 0L, up.maxOrNull() ?: 0L)).toFloat()
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(c.border, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        fun series(data: List<Long>, color: Color) {
            if (data.size < 2) return
            val step = size.width / (points - 1).toFloat()
            val startX = size.width - step * (data.size - 1)
            val line = Path()
            val area = Path()
            data.forEachIndexed { i, v ->
                val x = startX + step * i
                val y = size.height - (v.toFloat() / maxValue) * size.height * 0.92f - 2f
                if (i == 0) {
                    line.moveTo(x, y)
                    area.moveTo(x, size.height)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(startX + step * (data.size - 1), size.height)
            area.close()
            drawPath(area, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(
                line,
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        series(up, BrandViolet)
        series(down, BrandBlue)
    }
}
