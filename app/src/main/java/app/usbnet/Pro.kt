package app.usbnet

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Exact size with two decimals in MB (so the live counter visibly moves every second). */
fun formatExact(b: Long): String {
    if (b < 1024L * 1024L) return formatBytes(b)
    return String.format(java.util.Locale.US, "%.2f %s", b / 1048576.0, sizeUnit("MB"))
}

@Composable
fun Chevron(tint: Color, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AppIcon(Ic.Chevron, tint, size = size, modifier = Modifier.scale(if (rtl) -1f else 1f, 1f))
}

fun openUrl(ctx: Context, url: String) {
    if (url.isEmpty()) return
    safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// =================================================================================================
// Usage card: exact, live (updated every second by the tunnel service)

@Composable
fun UsageCard(onUpgrade: (() -> Unit)?) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val q by Quota.state.collectAsState()
    val ent by Billing.state.collectAsState()
    val fa = Prefs.effectiveLanguage(ctx) == "fa"
    val frac by animateFloatAsState(q.fraction, tween(400), label = "usage")
    val barColor = when {
        ent.pro -> AccentGreen
        q.fraction >= 0.95f -> AccentRed
        q.fraction >= 0.8f -> AccentAmber
        else -> BrandBlue
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (ent.pro) Ic.Crown else Ic.Chart, barColor, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppText(
                    stringResource(if (ent.pro) R.string.usage_title_pro else R.string.usage_title),
                    size = 15.sp, weight = FontWeight.Bold
                )
                AppText(stringResource(R.string.usage_trial_sub), size = 12.sp, color = c.muted)
            }
            if (!ent.pro && onUpgrade != null) {
                ChipButton(stringResource(R.string.upgrade_short), true, onUpgrade)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (ent.pro) {
            AppText(stringResource(R.string.usage_unlimited), size = 22.sp, weight = FontWeight.Bold, color = AccentGreen)
            Spacer(Modifier.height(2.dp))
            AppText(
                stringResource(R.string.usage_used_pro, formatExact(q.usedPro)),
                size = 13.sp, color = c.muted
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                AppText(formatExact(q.usedFree), size = 26.sp, weight = FontWeight.Bold, color = barColor)
                Spacer(Modifier.width(6.dp))
                AppText("/ " + formatExact(q.limit), size = 14.sp, color = c.muted, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.surface2)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .clip(RoundedCornerShape(50))
                        .background(barColor)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                AppText(
                    stringResource(R.string.usage_left, formatExact(q.remaining)),
                    modifier = Modifier.weight(1f), size = 12.sp, color = c.muted
                )
                AppText(
                    stringResource(R.string.usage_trial_once),
                    size = 12.sp, color = c.muted
                )
            }
        }
    }
}

// =================================================================================================
// Pro / subscription page

private class Row3(val title: Int, val free: String?, val pro: String?)

@Composable
fun ProScreen() {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val ent by Billing.state.collectAsState()
    val q by Quota.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(Billing.history(ctx)) }
    val fa = Prefs.effectiveLanguage(ctx) == "fa"

    val msgOk = stringResource(R.string.pro_toast_ok)
    val msgFail = stringResource(R.string.pro_toast_fail)
    val msgNoBazaar = stringResource(R.string.pro_need_bazaar)
    val msgFound = stringResource(R.string.restore_found)
    val msgNone = stringResource(R.string.restore_none)
    val msgConn = stringResource(R.string.restore_failed)

    // Keep the history list in sync whenever the subscription state changes (e.g. after the start-up check).
    LaunchedEffect(ent.pro, ent.verifiedAtMs) { history = Billing.history(ctx) }

    fun buy(sku: String) {
        val act = ctx.findActivity() ?: return
        busy = true
        Billing.purchase(act, sku) { r ->
            busy = false
            history = Billing.history(ctx)
            when (r) {
                PurchaseResult.Success -> Toast.makeText(ctx, msgOk, Toast.LENGTH_LONG).show()
                PurchaseResult.BazaarMissing -> Toast.makeText(ctx, msgNoBazaar, Toast.LENGTH_LONG).show()
                PurchaseResult.Failed -> Toast.makeText(ctx, msgFail, Toast.LENGTH_LONG).show()
                PurchaseResult.Cancelled -> {}
            }
        }
    }

    val rows = listOf(
        Row3(R.string.cmp_data, stringResource(R.string.cmp_data_free), stringResource(R.string.cmp_data_pro)),
        Row3(R.string.cmp_live, "✓", "✓"),
        Row3(R.string.cmp_pc, "✓", "✓"),
        Row3(R.string.cmp_routing, null, "✓"),
        Row3(R.string.cmp_timer, null, "✓"),
        Row3(R.string.cmp_notif, stringResource(R.string.cmp_notif_free), stringResource(R.string.cmp_notif_pro)),
        Row3(R.string.cmp_pc_auto, null, "✓"),
        Row3(R.string.cmp_report, null, "✓"),
        Row3(R.string.cmp_support, null, "✓"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Reveal(0) { ScreenHeader(stringResource(R.string.pro_title), stringResource(R.string.pro_sub)) }

        // Status
        Reveal(1) {
            GradientCard(if (ent.pro) GreenGradient else BrandGradient) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(Ic.Crown, Color.White, size = 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        AppText(
                            stringResource(if (ent.pro) R.string.pro_active else R.string.pro_free_plan),
                            size = 20.sp, weight = FontWeight.Bold, color = Color.White
                        )
                        val sub = when {
                            ent.pro && ent.plan == "yearly" -> stringResource(R.string.plan_yearly)
                            ent.pro && ent.plan == "monthly" -> stringResource(R.string.plan_monthly)
                            ent.pro -> stringResource(R.string.pro_unlimited_short)
                            else -> stringResource(R.string.pro_free_limit)
                        }
                        AppText(sub, size = 13.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                if (ent.pro && ent.verifiedAtMs > 0L) {
                    Spacer(Modifier.height(10.dp))
                    AppText(
                        stringResource(R.string.pro_verified, Jalali.dateLabel(ent.verifiedAtMs, fa)),
                        size = 12.sp, color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (ent.checking) {
                    Spacer(Modifier.height(6.dp))
                    AppText(stringResource(R.string.pro_checking), size = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        Reveal(2) { UsageCard(null) }

        // Buy
        if (!ent.pro) {
            Reveal(3) {
                AppCard {
                    AppText(stringResource(R.string.pro_choose_sub), size = 13.sp, color = c.muted, lineHeight = 19.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = stringResource(R.string.buy_sub),
                        onClick = { buy(BillingConfig.SKU_MAIN) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        icon = Ic.Crown
                    )
                    if (ent.error == BillingError.BazaarMissing || !Billing.isBazaarInstalled(ctx)) {
                        Spacer(Modifier.height(12.dp))
                        AppText(stringResource(R.string.pro_need_bazaar), size = 12.sp, color = AccentAmber, lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        GhostButton(
                            stringResource(R.string.pro_install_bazaar),
                            { openUrl(ctx, Brand.BAZAAR_INSTALL_URL) },
                            Modifier.fillMaxWidth(), BrandBlue
                        )
                    }
                }
            }
        }

        Reveal(4) {
            GhostButton(
                text = stringResource(if (ent.checking) R.string.pro_checking else R.string.pro_restore),
                onClick = {
                    if (!ent.checking) {
                        Billing.refresh {
                            history = Billing.history(ctx)
                            val st = Billing.state.value
                            val msg = when {
                                st.pro && st.live -> msgFound
                                st.error == BillingError.BazaarMissing -> msgNoBazaar
                                st.error != BillingError.None -> msgConn
                                st.pro -> msgFound
                                else -> msgNone
                            }
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                color = BrandBlue,
                filled = true
            )
        }

        // Comparison
        Reveal(5) {
            AppCard(padding = 14.dp) {
                AppText(stringResource(R.string.cmp_title), size = 16.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1.6f))
                    AppText(stringResource(R.string.cmp_free), Modifier.weight(1f), 12.sp, FontWeight.Bold, c.muted, TextAlign.Center)
                    AppText(stringResource(R.string.cmp_pro), Modifier.weight(1f), 12.sp, FontWeight.Bold, BrandViolet, TextAlign.Center)
                }
                rows.forEach { r ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(stringResource(r.title), Modifier.weight(1.6f), 13.sp, lineHeight = 18.sp)
                        Cell(Modifier.weight(1f), r.free, false)
                        Cell(Modifier.weight(1f), r.pro, true)
                    }
                }
            }
        }

        // History
        Reveal(6) {
            AppCard {
                AppText(stringResource(R.string.hist_title), size = 16.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (history.isEmpty()) {
                    AppText(stringResource(R.string.hist_empty), size = 13.sp, color = c.muted)
                } else {
                    history.take(12).forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val col = if (e.type == "ended") AccentRed else AccentGreen
                            IconBadge(if (e.type == "ended") Ic.Close else Ic.Check, col, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                AppText(
                                    stringResource(
                                        when (e.type) {
                                            "purchased" -> R.string.hist_purchased
                                            "ended" -> R.string.hist_ended
                                            else -> R.string.hist_active
                                        }
                                    ),
                                    size = 14.sp, weight = FontWeight.SemiBold
                                )
                                AppText(
                                    when (e.plan) {
                                        "yearly" -> stringResource(R.string.plan_yearly)
                                        "monthly" -> stringResource(R.string.plan_monthly)
                                        else -> stringResource(R.string.plan_pro)
                                    },
                                    size = 12.sp, color = c.muted
                                )
                            }
                            AppText(Jalali.dateLabel(e.timeMs, fa), size = 12.sp, color = c.muted)
                        }
                    }
                }
                val months = remember(q.period) { Quota.monthHistory() }
                if (months.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Spacer(Modifier.height(10.dp))
                    AppText(stringResource(R.string.hist_usage), size = 14.sp, weight = FontWeight.Bold)
                    months.take(6).forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            AppText(Jalali.monthLabel(m.first, fa), Modifier.weight(1f), 13.sp, color = c.muted)
                            AppText(formatBytes(m.second + m.third), size = 13.sp, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Price text is optional: leave the string empty and Cafe Bazaar shows the price in its payment screen. */
@Composable
private fun priceSuffix(res: Int): String {
    val p = stringResource(res)
    return if (p.isBlank()) "" else " · $p"
}

@Composable
private fun Cell(modifier: Modifier, value: String?, pro: Boolean) {
    val c = LocalAppColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            value == null -> AppText("—", size = 14.sp, color = c.muted)
            value == "✓" -> AppIcon(Ic.Check, if (pro) AccentGreen else c.muted, size = 18.dp)
            else -> AppText(
                value, size = 12.sp, weight = FontWeight.SemiBold,
                color = if (pro) AccentGreen else c.text, align = TextAlign.Center, lineHeight = 16.sp
            )
        }
    }
}

// =================================================================================================
// Dialogs

/** Shown when the free allowance is used up or a Pro feature is tapped. */
@Composable
fun PaywallDialog(reason: Int, onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    AppDialog(
        title = stringResource(R.string.paywall_title),
        onDismiss = onDismiss,
        icon = Ic.Crown,
        iconTint = AccentAmber,
        primaryText = stringResource(R.string.paywall_cta),
        onPrimary = onUpgrade,
        secondaryText = stringResource(R.string.later_btn),
        onSecondary = onDismiss
    ) {
        AppText(stringResource(reason), size = 14.sp, color = c.text, lineHeight = 22.sp, align = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface2)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(R.string.pw_f1, R.string.pw_f2, R.string.pw_f3, R.string.pw_f4).forEach {
                Row(verticalAlignment = Alignment.Top) {
                    AppIcon(Ic.Check, AccentGreen, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    AppText(stringResource(it), size = 13.sp, color = c.text, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** Once per new version: what's new, with a button to the store. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val fa = Prefs.effectiveLanguage(ctx) == "fa"
    AppDialog(
        title = stringResource(R.string.upd_title, info.name),
        onDismiss = onDismiss,
        icon = Ic.Download,
        primaryText = stringResource(R.string.upd_btn),
        onPrimary = { Updates.openStore(ctx); onDismiss() },
        secondaryText = stringResource(R.string.later_btn),
        onSecondary = onDismiss
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppText(stringResource(R.string.upd_whats_new), size = 13.sp, color = c.muted)
            info.notes(fa).forEach {
                AppText("• $it", size = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** Full-screen block for versions below the minimum. The only exception: update-only connect. */
@Composable
fun ForceUpdateScreen(info: UpdateInfo?, connected: Boolean, onUpdateOnlyConnect: () -> Unit, onStop: () -> Unit) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val fa = Prefs.effectiveLanguage(ctx) == "fa"
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(30.dp))
        LogoMark(size = 84.dp)
        Spacer(Modifier.height(20.dp))
        AppText(stringResource(R.string.force_title), size = 24.sp, weight = FontWeight.Bold, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AppText(stringResource(R.string.force_body), size = 14.sp, color = c.muted, align = TextAlign.Center, lineHeight = 22.sp)
        val notes = info?.notes(fa).orEmpty()
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AppCard {
                AppText(stringResource(R.string.upd_whats_new), size = 13.sp, color = c.muted)
                Spacer(Modifier.height(6.dp))
                notes.forEach { AppText("• $it", size = 14.sp, lineHeight = 21.sp) }
            }
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton(stringResource(R.string.upd_btn), { Updates.openStore(ctx) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (connected) {
            GhostButton(stringResource(R.string.force_stop_link), onStop, Modifier.fillMaxWidth(), AccentRed)
        } else {
            GhostButton(stringResource(R.string.force_connect_only), onUpdateOnlyConnect, Modifier.fillMaxWidth(), BrandBlue)
            Spacer(Modifier.height(8.dp))
            AppText(stringResource(R.string.force_connect_only_hint), size = 12.sp, color = c.muted, align = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}
