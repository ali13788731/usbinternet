package app.usbnet

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.util.Locale

// =================================================================================================
// Formatting

private fun isFa(): Boolean = LocaleHelper.fa

/** Localised unit label: Persian names when the app language is Persian. */
fun sizeUnit(u: String): String = if (!isFa()) u else when (u) {
    "B" -> "بایت"
    "KB" -> "کیلوبایت"
    "MB" -> "مگابایت"
    "GB" -> "گیگابایت"
    "B/s" -> "بایت/ثانیه"
    "KB/s" -> "کیلوبایت/ثانیه"
    "MB/s" -> "مگابایت/ثانیه"
    "Mbps" -> "مگابیت/ثانیه"
    else -> u
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> String.format(Locale.US, "%d %s", b, sizeUnit("B"))
    b < 1024L * 1024 -> String.format(Locale.US, "%.1f %s", b / 1024.0, sizeUnit("KB"))
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f %s", b / 1048576.0, sizeUnit("MB"))
    else -> String.format(Locale.US, "%.2f %s", b / 1073741824.0, sizeUnit("GB"))
}

fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

/** [bps] is bytes per second; [unit] is Prefs.UNIT_MBS or Prefs.UNIT_MBPS. */
fun formatSpeed(bps: Long, unit: Int): String {
    if (unit == Prefs.UNIT_MBPS) {
        return String.format(Locale.US, "%.2f %s", bps * 8.0 / 1_000_000.0, sizeUnit("Mbps"))
    }
    return when {
        bps < 1024 -> String.format(Locale.US, "%d %s", bps, sizeUnit("B/s"))
        bps < 1024L * 1024 -> String.format(Locale.US, "%.1f %s", bps / 1024.0, sizeUnit("KB/s"))
        else -> String.format(Locale.US, "%.2f %s", bps / 1048576.0, sizeUnit("MB/s"))
    }
}

// =================================================================================================
// Android helpers

fun isUsbDebuggingOn(ctx: Context): Boolean = try {
    Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
} catch (e: Exception) {
    false
}

/** Starts an activity and quietly ignores phones that do not have it. Returns true when it worked. */
fun safeStart(ctx: Context, intent: Intent): Boolean {
    return try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

/** Opens Developer options, or About phone (to unlock them), or plain Settings as a last resort. */
fun openDeveloperSettings(ctx: Context) {
    val attempts = listOf(
        Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        Settings.ACTION_DEVICE_INFO_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in attempts) {
        if (safeStart(ctx, Intent(action))) return
    }
}

/** Returns a number that grows every time the screen comes back to the foreground. */
@Composable
fun rememberResumeTick(): Int {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = ctx as? ComponentActivity
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick += 1
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return tick
}

// =================================================================================================
// Modifiers

/** Click with a small springy "press" animation (no ripple). */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val factor by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press"
    )
    this
        .scale(factor)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// =================================================================================================
// Building blocks

/** B Titr everywhere (bundled in res/font; a single bold face is used for every weight). */
val AppFontFamily = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.btitr_bold, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.btitr_bold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.btitr_bold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.btitr_bold, FontWeight.Bold),
)

/** Persian digits (and the Persian decimal mark) when the app language is Persian. */
fun localizeDigits(s: String): String {
    if (!LocaleHelper.fa || s.isEmpty()) return s
    val hasLatin = s.any { it in 'a'..'z' || it in 'A'..'Z' }
    val sb = StringBuilder(s.length)
    for (i in s.indices) {
        val ch = s[i]
        when {
            ch in '0'..'9' -> sb.append('\u06F0' + (ch - '0'))
            ch == '.' && !hasLatin && i > 0 && i < s.length - 1 && s[i - 1] in '0'..'9' && s[i + 1] in '0'..'9' -> sb.append('\u066B')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalAppColors.current.text,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = localizeDigits(text),
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = AppFontFamily,
        textAlign = align ?: TextAlign.Start,
        lineHeight = lineHeight,
        maxLines = maxLines,
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun GradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(brush, shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
fun IconBadge(ic: Ic, tint: Color, size: Dp = 42.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(ic, tint, size = size * 0.52f)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
        size = 13.sp,
        weight = FontWeight.SemiBold,
        color = LocalAppColors.current.muted
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
        AppText(title, size = 28.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        AppText(subtitle, size = 14.sp, color = LocalAppColors.current.muted)
    }
}

@Composable
fun SettingRow(
    ic: Ic,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = LocalAppColors.current
    val clickMod = if (onClick != null) Modifier.bounceClick(true, onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(clickMod)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(ic, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AppText(title, size = 15.sp, weight = FontWeight.SemiBold)
            if (subtitle != null) {
                AppText(subtitle, size = 12.sp, color = c.muted, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = LocalAppColors.current
    val track by animateColorAsState(
        targetValue = if (checked) BrandBlue else c.surface2,
        animationSpec = tween(220),
        label = "switchTrack"
    )
    val thumb by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(220),
        label = "switchThumb"
    )
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, c.border, CircleShape)
            .bounceClick(enabled) { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumb)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun ToggleRow(
    ic: Ic,
    tint: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingRow(
        ic = ic,
        tint = tint,
        title = title,
        subtitle = subtitle,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = { AppSwitch(checked, onChange, enabled) }
    )
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalAppColors.current
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(50))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val sel = index == selected
            val bg by animateColorAsState(
                targetValue = if (sel) BrandBlue else Color.Transparent,
                animationSpec = tween(220),
                label = "segBg"
            )
            val fg by animateColorAsState(
                targetValue = if (sel) Color.White else c.muted,
                animationSpec = tween(220),
                label = "segFg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .bounceClick(enabled) { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    label,
                    size = 13.sp,
                    weight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    align = TextAlign.Center
                )
            }
        }
    }
}

/** Small selectable chip (phone brand tabs, the short "upgrade" pill). */
@Composable
fun ChipButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) BrandBlue else c.surface2,
        animationSpec = tween(220),
        label = "chipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else c.text,
        animationSpec = tween(220),
        label = "chipFg"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.18f) else c.border, RoundedCornerShape(50))
            .bounceClick(true, onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(text, size = 13.sp, weight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

// ---- Buttons: one family. Full pill shape, solid fill (renders the same on every device), soft glow for the main one.

@Composable
private fun SolidPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    fill: Color,
    fg: Color,
    minHeight: Dp,
    glow: Boolean,
    enabled: Boolean,
    icon: Ic? = null,
    textSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    val shape = RoundedCornerShape(50)
    val alphaFill = if (enabled) fill else fill.copy(alpha = 0.45f)
    Box(
        modifier
            .then(if (glow && enabled) Modifier.shadow(10.dp, shape, ambientColor = fill, spotColor = fill) else Modifier)
            .clip(shape)
            .background(alphaFill, shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .bounceClick(enabled, onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                AppIcon(icon, fg, size = 20.dp)
                Spacer(Modifier.width(8.dp))
            }
            AppText(text, size = textSize, weight = FontWeight.Bold, color = fg, align = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Main action. Teal pill with a soft glow; the amber variant is kept for "special" offers. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = BrandGradient,
    enabled: Boolean = true,
    icon: Ic? = null,
) {
    val base = if (brush === AmberGradient) AccentAmber else BrandBlue
    SolidPill(text, onClick, modifier, base, Color.White, 54.dp, true, enabled, icon)
}

/** Quiet outlined pill ("Later", "Cancel", "Install Bazaar"...). */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.muted,
    filled: Boolean = false,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    val isNeutral = color == c.muted
    val line = if (isNeutral) c.border else color.copy(alpha = 0.55f)
    val fg = if (isNeutral) c.text else color
    Box(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.background(if (isNeutral) c.surface2 else color.copy(alpha = 0.12f)) else Modifier)
            .border(1.5.dp, line, shape)
            .bounceClick(true, onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(text, size = 15.sp, weight = FontWeight.SemiBold, color = fg, align = TextAlign.Center, maxLines = 2)
    }
}

/** Secondary pill with a tinted (opaque) fill: Back, Not now... */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val fill = if (c.dark) Color(0xFF15302D) else Color(0xFFD5EEEA)
    val fg = if (c.dark) Color(0xFF7FE3D6) else Color(0xFF0B6B62)
    SolidPill(text, onClick, modifier, fill, fg, 50.dp, false, true, null, 15.sp)
}

/** Dialog confirm button (kept for callers that still use AlertDialog slots). */
@Composable
fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SolidPill(text, onClick, modifier, BrandBlue, Color.White, 46.dp, false, true, null, 15.sp)
}

/** Small round icon-only button (back arrow...). */
@Composable
fun RoundIconButton(ic: Ic, onClick: () -> Unit, modifier: Modifier = Modifier, flip: Boolean = false) {
    val c = LocalAppColors.current
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(c.surface2, CircleShape)
            .border(1.dp, c.border, CircleShape)
            .bounceClick(true, onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(ic, c.text, size = 22.dp, modifier = Modifier.scale(if (flip) -1f else 1f, 1f))
    }
}

/**
 * The one dialog of the app: rounded card, round icon on top, centered title, content, and the buttons stacked
 * full width (main action first, quiet action under it).
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Ic? = null,
    iconTint: Color = BrandBlue,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface, shape)
                .border(1.dp, c.border, shape)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { AppIcon(icon, iconTint, size = 30.dp) }
                Spacer(Modifier.height(14.dp))
            }
            AppText(title, size = 20.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(22.dp))
            PrimaryButton(primaryText, onPrimary, Modifier.fillMaxWidth())
            if (secondaryText != null && onSecondary != null) {
                Spacer(Modifier.height(10.dp))
                GhostButton(secondaryText, onSecondary, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        AppText(text, size = 12.sp, weight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
fun Dots(count: Int, index: Int) {
    val c = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val sel = i == index
            val w by animateDpAsState(
                targetValue = if (sel) 24.dp else 8.dp,
                animationSpec = tween(250),
                label = "dotW"
            )
            val col by animateColorAsState(
                targetValue = if (sel) BrandBlue else c.border,
                animationSpec = tween(250),
                label = "dotC"
            )
            Box(
                Modifier
                    .size(width = w, height = 8.dp)
                    .clip(CircleShape)
                    .background(col)
            )
        }
    }
}

/** Fade + slide in with a small stagger. */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 6 }
    ) {
        content()
    }
}

/** Numbered step with an accent badge. */
@Composable
fun StepItem(number: Int, text: String, done: Boolean = false) {
    val c = LocalAppColors.current
    val badge = if (done) AccentGreen else BrandBlue
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(badge.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                AppIcon(Ic.Check, badge, size = 16.dp)
            } else {
                AppText(
                    String.format(Locale.US, "%d", number),
                    size = 13.sp,
                    weight = FontWeight.Bold,
                    color = badge
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppText(
            text,
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
            size = 14.sp,
            color = c.text,
            lineHeight = 21.sp
        )
    }
}

/** Shows whether USB debugging is on and offers a one-tap shortcut to the right settings page. */
@Composable
fun UsbDebugRow() {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val enabled = remember(tick) { isUsbDebuggingOn(ctx) }
    val c = LocalAppColors.current
    val tint = if (enabled) AccentGreen else AccentAmber
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Ic.Dev, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AppText(
                stringResource(if (enabled) R.string.usbdebug_on else R.string.usbdebug_off),
                size = 15.sp,
                weight = FontWeight.SemiBold,
                color = tint
            )
            if (!enabled) {
                AppText(
                    stringResource(R.string.usbdebug_hint),
                    size = 12.sp,
                    color = c.muted,
                    lineHeight = 17.sp
                )
            }
        }
        if (!enabled) {
            Spacer(Modifier.width(10.dp))
            ChipButton(stringResource(R.string.usbdebug_open), true) { openDeveloperSettings(ctx) }
        }
    }
}
