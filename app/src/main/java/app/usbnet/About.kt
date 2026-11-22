package app.usbnet

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat

fun appVersionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) {
    "1.0"
}

fun appVersionCode(ctx: Context): Long = try {
    PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
} catch (e: Exception) {
    1L
}

private fun openBazaarRating(ctx: Context) {
    val edit = Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=" + ctx.packageName))
        .setPackage("com.farsitel.bazaar")
    if (!safeStart(ctx, edit)) {
        safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(Brand.STORE_URL)))
    }
}

private fun shareApp(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    safeStart(ctx, Intent.createChooser(send, null))
}

@Composable
fun AboutScreen(onReplayIntro: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    val versionName = remember { appVersionName(ctx) }
    var updMsg by remember { mutableStateOf("") }
    val msgLatest = stringResource(R.string.upd_latest)
    val msgFail = stringResource(R.string.upd_check_failed)
    val msgNew = stringResource(R.string.upd_new_found)

    val infinite = rememberInfiniteTransition(label = "about")
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breathe"
    )

    val shareText = stringResource(R.string.about_share_text, Brand.STORE_URL)
    val hasContact = Brand.TELEGRAM.isNotEmpty() || Brand.EMAIL.isNotEmpty() || Brand.WEBSITE.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Reveal(0) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(220.dp)) {
                        val half = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(BrandViolet.copy(alpha = 0.30f), Color.Transparent),
                                center = center,
                                radius = half
                            ),
                            radius = half,
                            center = center
                        )
                    }
                    LogoMark(size = 112.dp, modifier = Modifier.scale(breathe))
                }
                AppText(
                    stringResource(R.string.app_name),
                    size = 28.sp,
                    weight = FontWeight.Bold,
                    align = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                AppText(
                    stringResource(R.string.about_version, versionName),
                    size = 13.sp,
                    color = c.muted
                )
            }
        }

        Reveal(1) {
            AppCard {
                AppText(stringResource(R.string.about_desc), size = 15.sp, lineHeight = 23.sp)
            }
        }

        Reveal(2) {
            AppCard {
                AppText(stringResource(R.string.about_features_title), size = 17.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                stringArrayResource(R.array.about_features).forEach { item ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        AppIcon(Ic.Check, AccentGreen, size = 20.dp, modifier = Modifier.padding(top = 1.dp))
                        Spacer(Modifier.width(12.dp))
                        AppText(item, modifier = Modifier.weight(1f), size = 14.sp, lineHeight = 21.sp)
                    }
                }
            }
        }

        Reveal(3) {
            InfoCard(Ic.Pulse, BrandBlue, R.string.about_how_title, R.string.about_how_body)
        }
        Reveal(4) {
            InfoCard(Ic.Shield, AccentGreen, R.string.about_privacy_title, R.string.about_privacy_body)
        }
        Reveal(5) {
            InfoCard(Ic.Dev, BrandViolet, R.string.about_oss_title, R.string.about_oss_body)
        }

        Reveal(6) {
            AppCard {
                SettingRow(
                    ic = Ic.Star,
                    tint = AccentAmber,
                    title = stringResource(R.string.about_rate),
                    onClick = { openBazaarRating(ctx) }
                )
                SettingRow(
                    ic = Ic.Share,
                    tint = BrandBlue,
                    title = stringResource(R.string.about_share),
                    onClick = { shareApp(ctx, shareText) }
                )
                SettingRow(
                    ic = Ic.Download,
                    tint = AccentGreen,
                    title = stringResource(R.string.upd_check),
                    subtitle = updMsg.ifEmpty { null },
                    onClick = {
                        updMsg = ""
                        Updates.checkAsync(ctx) { ok ->
                            updMsg = when {
                                !ok -> msgFail
                                Updates.available(ctx) -> msgNew
                                else -> msgLatest
                            }
                            if (ok && Updates.available(ctx)) Updates.openStore(ctx)
                        }
                    }
                )
            }
        }

        if (hasContact) {
            Reveal(7) {
                AppCard {
                    AppText(stringResource(R.string.about_contact_title), size = 17.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (Brand.TELEGRAM.isNotEmpty()) {
                        SettingRow(
                            ic = Ic.Plane,
                            tint = AccentCyan,
                            title = stringResource(R.string.about_telegram),
                            onClick = { safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(Brand.TELEGRAM))) }
                        )
                    }
                    if (Brand.EMAIL.isNotEmpty()) {
                        SettingRow(
                            ic = Ic.Mail,
                            tint = AccentAmber,
                            title = stringResource(R.string.about_email),
                            subtitle = Brand.EMAIL,
                            onClick = { safeStart(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Brand.EMAIL))) }
                        )
                    }
                    if (Brand.WEBSITE.isNotEmpty()) {
                        SettingRow(
                            ic = Ic.Globe,
                            tint = BrandBlue,
                            title = stringResource(R.string.about_website),
                            onClick = { safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(Brand.WEBSITE))) }
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun InfoCard(ic: Ic, tint: Color, title: Int, body: Int) {
    val c = LocalAppColors.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ic, tint)
            Spacer(Modifier.width(14.dp))
            AppText(stringResource(title), size = 17.sp, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        AppText(stringResource(body), size = 14.sp, color = c.muted, lineHeight = 22.sp)
    }
}
