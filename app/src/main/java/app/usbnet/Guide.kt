package app.usbnet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class BrandGuide(val nameRes: Int, val stepsRes: Int)

private val brandGuides = listOf(
    BrandGuide(R.string.brand_generic, R.array.g_generic),
    BrandGuide(R.string.brand_samsung, R.array.g_samsung),
    BrandGuide(R.string.brand_xiaomi, R.array.g_xiaomi),
    BrandGuide(R.string.brand_huawei, R.array.g_huawei),
    BrandGuide(R.string.brand_oppo, R.array.g_oppo),
    BrandGuide(R.string.brand_vivo, R.array.g_vivo),
    BrandGuide(R.string.brand_trouble, R.array.g_trouble),
)

@Composable
fun GuideScreen() {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    var selected by remember { mutableIntStateOf(0) }
    val troubleIndex = brandGuides.size - 1

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Reveal(0) {
            ScreenHeader(stringResource(R.string.guide_title), stringResource(R.string.guide_sub))
        }

        Reveal(1) {
            AppCard {
                UsbDebugRow()
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    text = stringResource(R.string.usbdebug_open),
                    onClick = { openDeveloperSettings(ctx) },
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandBlue
                )
            }
        }

        Reveal(2) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Ic.Screen, BrandBlue)
                    Spacer(Modifier.width(14.dp))
                    AppText(stringResource(R.string.guide_pc_title), size = 17.sp, weight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                val pcSteps = stringArrayResource(R.array.g_pc)
                pcSteps.forEachIndexed { i, text -> StepItem(i + 1, text) }
            }
        }

        Reveal(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Ic.Phone, AccentAmber)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        AppText(stringResource(R.string.guide_phone_title), size = 17.sp, weight = FontWeight.Bold)
                        AppText(stringResource(R.string.guide_brand_title), size = 12.sp, color = c.muted)
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    brandGuides.forEachIndexed { i, b ->
                        ChipButton(stringResource(b.nameRes), i == selected) { selected = i }
                    }
                }
            }
        }

        Reveal(4) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                label = "brandSteps"
            ) { sel ->
                AppCard {
                    val steps = stringArrayResource(brandGuides[sel].stepsRes)
                    val warn = sel == troubleIndex
                    steps.forEachIndexed { i, text ->
                        if (warn) {
                            TroubleItem(text)
                        } else {
                            StepItem(i + 1, text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleItem(text: String) {
    val c = LocalAppColors.current
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        AppIcon(Ic.About, AccentAmber, size = 22.dp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        AppText(text, modifier = Modifier.weight(1f), size = 14.sp, color = c.text, lineHeight = 21.sp)
    }
}
