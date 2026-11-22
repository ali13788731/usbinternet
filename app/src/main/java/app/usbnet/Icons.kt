package app.usbnet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** All icons are drawn by hand with Canvas so no icon library is needed. */
enum class Ic {
    Home, Guide, Settings, About, Globe, Contrast, Shield, Bell, Battery, Dev, Star, Share,
    Reset, Play, Check, Close, Dns, Phone, Pulse, Speed, Power, Down, Up, Lock, Mail, Plane, Screen, Vibrate,
    Crown, Download, Chevron, Clock, Chart, Apps, Bolt, Computer
}

@Composable
fun AppIcon(ic: Ic, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIcon(ic, tint) }
}

/** Draws [ic] inside the current canvas, using a 24 x 24 design grid. */
fun DrawScope.drawIcon(ic: Ic, color: Color) {
    val s = size.minDimension / 24f
    val w = 2f * s
    val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun pt(x: Float, y: Float) = Offset(x * s, y * s)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(color, pt(x1, y1), pt(x2, y2), strokeWidth = w, cap = StrokeCap.Round)
    }

    fun poly(close: Boolean, vararg pts: Float) {
        val path = Path()
        var i = 0
        while (i + 1 < pts.size) {
            val x = pts[i] * s
            val y = pts[i + 1] * s
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i += 2
        }
        if (close) path.close()
        drawPath(path, color, style = st)
    }

    fun ring(cx: Float, cy: Float, r: Float) {
        drawCircle(color, radius = r * s, center = pt(cx, cy), style = st)
    }

    fun dot(cx: Float, cy: Float, r: Float) {
        drawCircle(color, radius = r * s, center = pt(cx, cy))
    }

    fun box(x: Float, y: Float, bw: Float, bh: Float, r: Float) {
        drawRoundRect(
            color,
            topLeft = pt(x, y),
            size = Size(bw * s, bh * s),
            cornerRadius = CornerRadius(r * s, r * s),
            style = st
        )
    }

    when (ic) {
        Ic.Home -> {
            poly(false, 3f, 11f, 12f, 3f, 21f, 11f)
            poly(false, 5f, 10f, 5f, 20f, 10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f, 19f, 20f, 19f, 10f)
        }
        Ic.Guide -> {
            line(10f, 6f, 20f, 6f)
            line(10f, 12f, 20f, 12f)
            line(10f, 18f, 20f, 18f)
            dot(5f, 6f, 1.5f)
            dot(5f, 12f, 1.5f)
            dot(5f, 18f, 1.5f)
        }
        Ic.Settings -> {
            line(4f, 7f, 20f, 7f)
            line(4f, 17f, 20f, 17f)
            ring(9f, 7f, 2.6f)
            ring(15f, 17f, 2.6f)
        }
        Ic.About -> {
            ring(12f, 12f, 9f)
            line(12f, 11f, 12f, 16.5f)
            dot(12f, 7.8f, 1.2f)
        }
        Ic.Globe -> {
            ring(12f, 12f, 9f)
            line(3f, 12f, 21f, 12f)
            drawOval(color, topLeft = pt(8f, 3f), size = Size(8f * s, 18f * s), style = st)
        }
        Ic.Contrast -> {
            ring(12f, 12f, 8.5f)
            drawArc(
                color,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = pt(3.5f, 3.5f),
                size = Size(17f * s, 17f * s)
            )
        }
        Ic.Shield -> {
            val path = Path()
            path.moveTo(12f * s, 3f * s)
            path.lineTo(19f * s, 6f * s)
            path.lineTo(19f * s, 12f * s)
            path.cubicTo(19f * s, 16.5f * s, 15.8f * s, 19.8f * s, 12f * s, 21f * s)
            path.cubicTo(8.2f * s, 19.8f * s, 5f * s, 16.5f * s, 5f * s, 12f * s)
            path.lineTo(5f * s, 6f * s)
            path.close()
            drawPath(path, color, style = st)
            poly(false, 9f, 12f, 11.2f, 14.2f, 15f, 10f)
        }
        Ic.Bell -> {
            val path = Path()
            path.moveTo(6f * s, 16f * s)
            path.lineTo(6f * s, 11f * s)
            path.cubicTo(6f * s, 7.7f * s, 8.7f * s, 5f * s, 12f * s, 5f * s)
            path.cubicTo(15.3f * s, 5f * s, 18f * s, 7.7f * s, 18f * s, 11f * s)
            path.lineTo(18f * s, 16f * s)
            path.lineTo(20f * s, 18f * s)
            path.lineTo(4f * s, 18f * s)
            path.close()
            drawPath(path, color, style = st)
            line(10f, 21f, 14f, 21f)
        }
        Ic.Battery -> {
            box(3f, 7f, 15f, 10f, 2f)
            line(20.5f, 10f, 20.5f, 14f)
            drawRect(color, topLeft = pt(5.5f, 9.5f), size = Size(6f * s, 5f * s))
        }
        Ic.Dev -> {
            poly(false, 8f, 7f, 3f, 12f, 8f, 17f)
            poly(false, 16f, 7f, 21f, 12f, 16f, 17f)
            line(14f, 5f, 10f, 19f)
        }
        Ic.Star -> {
            val path = Path()
            for (i in 0 until 10) {
                val ang = Math.toRadians((-90 + i * 36).toDouble())
                val r = if (i % 2 == 0) 9.5f else 4.2f
                val x = (12f + r * cos(ang).toFloat()) * s
                val y = (12.5f + r * sin(ang).toFloat()) * s
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = st)
        }
        Ic.Share -> {
            ring(6f, 12f, 2.5f)
            ring(18f, 6f, 2.5f)
            ring(18f, 18f, 2.5f)
            line(8.3f, 10.9f, 15.7f, 7.1f)
            line(8.3f, 13.1f, 15.7f, 16.9f)
        }
        Ic.Reset -> {
            drawArc(
                color,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = pt(4f, 4f),
                size = Size(16f * s, 16f * s),
                style = st
            )
            poly(false, 16.8f, 1.8f, 16f, 5.2f, 12.6f, 4.4f)
        }
        Ic.Play -> {
            val path = Path()
            path.moveTo(8f * s, 5f * s)
            path.lineTo(19f * s, 12f * s)
            path.lineTo(8f * s, 19f * s)
            path.close()
            drawPath(path, color)
        }
        Ic.Check -> poly(false, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
        Ic.Close -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }
        Ic.Dns -> {
            box(4f, 4f, 16f, 6f, 2f)
            box(4f, 14f, 16f, 6f, 2f)
            dot(8f, 7f, 1.1f)
            dot(8f, 17f, 1.1f)
        }
        Ic.Phone -> {
            box(7f, 2f, 10f, 20f, 2.5f)
            line(10.5f, 19f, 13.5f, 19f)
        }
        Ic.Pulse -> poly(false, 3f, 13f, 8f, 13f, 10.5f, 6f, 14f, 19f, 16.5f, 12f, 21f, 12f)
        Ic.Speed -> {
            drawArc(
                color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = pt(3f, 6f),
                size = Size(18f * s, 18f * s),
                style = st
            )
            line(12f, 15f, 16.5f, 9.5f)
            dot(12f, 15f, 1.3f)
        }
        Ic.Power -> {
            drawArc(
                color,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = pt(5f, 6f),
                size = Size(14f * s, 14f * s),
                style = st
            )
            line(12f, 3f, 12f, 12f)
        }
        Ic.Down -> {
            line(12f, 4f, 12f, 19f)
            poly(false, 6f, 13f, 12f, 19f, 18f, 13f)
        }
        Ic.Up -> {
            line(12f, 20f, 12f, 5f)
            poly(false, 6f, 11f, 12f, 5f, 18f, 11f)
        }
        Ic.Lock -> {
            box(5f, 11f, 14f, 10f, 2f)
            drawArc(
                color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = pt(8f, 4f),
                size = Size(8f * s, 8f * s),
                style = st
            )
            line(8f, 8f, 8f, 11f)
            line(16f, 8f, 16f, 11f)
        }
        Ic.Mail -> {
            box(3f, 5f, 18f, 14f, 2f)
            poly(false, 3.5f, 7.5f, 12f, 13.5f, 20.5f, 7.5f)
        }
        Ic.Plane -> {
            poly(true, 21f, 3f, 3f, 11f, 10f, 13.5f, 13f, 21f)
            line(10f, 13.5f, 21f, 3f)
        }
        Ic.Screen -> {
            box(3f, 5f, 18f, 12f, 2f)
            line(9f, 20f, 15f, 20f)
            line(12f, 17f, 12f, 20f)
        }
        Ic.Vibrate -> {
            box(8f, 4f, 8f, 16f, 2f)
            line(4f, 8f, 4f, 16f)
            line(20f, 8f, 20f, 16f)
        }
        Ic.Crown -> {
            poly(true, 3f, 8f, 8f, 12f, 12f, 5f, 16f, 12f, 21f, 8f, 19f, 18f, 5f, 18f)
        }
        Ic.Download -> {
            line(12f, 4f, 12f, 15f)
            poly(false, 7f, 11f, 12f, 16f, 17f, 11f)
            line(5f, 20f, 19f, 20f)
        }
        Ic.Chevron -> {
            poly(false, 9f, 5f, 16f, 12f, 9f, 19f)
        }
        Ic.Clock -> {
            ring(12f, 12f, 9f)
            poly(false, 12f, 7f, 12f, 12f, 15.5f, 14f)
        }
        Ic.Chart -> {
            line(5f, 20f, 5f, 12f)
            line(12f, 20f, 12f, 5f)
            line(19f, 20f, 19f, 9f)
        }
        Ic.Apps -> {
            box(4f, 4f, 6f, 6f, 1.5f)
            box(14f, 4f, 6f, 6f, 1.5f)
            box(4f, 14f, 6f, 6f, 1.5f)
            box(14f, 14f, 6f, 6f, 1.5f)
        }
        Ic.Bolt -> {
            poly(true, 13f, 3f, 5f, 13f, 11f, 13f, 10f, 21f, 19f, 10f, 13f, 10f)
        }
        Ic.Computer -> {
            box(3f, 4f, 18f, 12f, 2f)
            line(8f, 20f, 16f, 20f)
            line(12f, 16f, 12f, 20f)
        }
    }
}

/** The USB trident symbol, drawn on a 100 x 100 grid scaled to the canvas. */
fun DrawScope.drawTrident(color: Color) {
    val u = size.minDimension / 100f
    fun pt(x: Float, y: Float) = Offset(x * u, y * u)
    val stroke = Stroke(width = 6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // stem
    drawLine(color, pt(50f, 80f), pt(50f, 24f), strokeWidth = 6f * u, cap = StrokeCap.Round)
    // arrow head
    val head = Path()
    head.moveTo(50f * u, 12f * u)
    head.lineTo(41f * u, 27f * u)
    head.lineTo(59f * u, 27f * u)
    head.close()
    drawPath(head, color)
    // bottom circle
    drawCircle(color, radius = 9f * u, center = pt(50f, 82f))
    // left branch ending in a circle
    val left = Path()
    left.moveTo(50f * u, 60f * u)
    left.lineTo(30f * u, 48f * u)
    left.lineTo(30f * u, 40f * u)
    drawPath(left, color, style = stroke)
    drawCircle(color, radius = 6.5f * u, center = pt(30f, 34f))
    // right branch ending in a square
    val right = Path()
    right.moveTo(50f * u, 48f * u)
    right.lineTo(70f * u, 38f * u)
    right.lineTo(70f * u, 32f * u)
    drawPath(right, color, style = stroke)
    drawRect(color, topLeft = pt(63.5f, 20f), size = Size(13f * u, 13f * u))
}

/** App logo: white trident on a rounded square with the brand gradient. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.26f
        drawRoundRect(
            brush = BrandGradient,
            cornerRadius = CornerRadius(r, r),
            size = this.size
        )
        drawTrident(Color.White)
    }
}
