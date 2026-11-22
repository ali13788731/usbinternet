package app.usbnet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private const val LAST_STEP = 3

/**
 * First-run introduction. Permissions are explained on step 3 first; the real Android request
 * is only made when the user taps the Allow button there.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    var step by remember { mutableIntStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = LAST_STEP }

    val needsNotifPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(c.bg, if (c.dark) Color(0xFF111A3A) else Color(0xFFE8EDFC))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (step < LAST_STEP) {
                    AppText(
                        stringResource(R.string.skip_btn),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (c.dark) Color(0xFF22306B) else Color(0xFFDCE5FF))
                            .bounceClick(true) { step = LAST_STEP }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        size = 14.sp,
                        weight = FontWeight.SemiBold,
                        color = if (c.dark) Color(0xFFBFD0FF) else Color(0xFF2F57D6)
                    )
                }
            }

            val dir = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1 else 1
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(350)) { dir * it / 3 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(350)) { -dir * it / 3 } + fadeOut(tween(250)))
                    } else {
                        (slideInHorizontally(tween(350)) { -dir * it / 3 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(350)) { dir * it / 3 } + fadeOut(tween(250)))
                    }
                },
                label = "onboarding"
            ) { s ->
                when (s) {
                    0 -> IntroStep()
                    1 -> HowStep()
                    2 -> PermissionStep()
                    else -> ReadyStep()
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Dots(count = LAST_STEP + 1, index = step)
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    2 -> {
                        // Full-width main button, secondary actions below it
                        PrimaryButton(
                            text = stringResource(
                                if (needsNotifPermission) R.string.ob_allow_notif else R.string.continue_btn
                            ),
                            onClick = {
                                if (needsNotifPermission) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    step = LAST_STEP
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TonalButton(stringResource(R.string.back_btn), { step -= 1 }, Modifier.weight(1f))
                            if (needsNotifPermission) {
                                TonalButton(stringResource(R.string.not_now), { step = LAST_STEP }, Modifier.weight(1f))
                            }
                        }
                    }
                    LAST_STEP -> PrimaryButton(
                        text = stringResource(R.string.start_btn),
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 0) {
                            TonalButton(stringResource(R.string.back_btn), { step -= 1 }, Modifier.weight(1f))
                        }
                        PrimaryButton(
                            text = stringResource(R.string.next_btn),
                            onClick = { step += 1 },
                            modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
                            brush = BrandGradient
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepScaffold(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Composable
private fun IntroStep() {
    val c = LocalAppColors.current
    val infinite = rememberInfiniteTransition(label = "intro")
    val bob by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "bob"
    )
    StepScaffold {
        LogoMark(size = 132.dp, modifier = Modifier.offset(y = bob.dp))
        Spacer(Modifier.height(30.dp))
        AppText(
            stringResource(R.string.ob1_title),
            size = 28.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center,
            lineHeight = 36.sp
        )
        Spacer(Modifier.height(14.dp))
        AppText(
            stringResource(R.string.ob1_body),
            size = 16.sp,
            color = c.muted,
            align = TextAlign.Center,
            lineHeight = 25.sp
        )
    }
}

@Composable
private fun HowStep() {
    StepScaffold {
        AppText(
            stringResource(R.string.ob2_title),
            size = 28.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        HowCard(Ic.Screen, BrandBlue, stringResource(R.string.ob2_s1))
        Spacer(Modifier.height(12.dp))
        HowCard(Ic.Dev, AccentAmber, stringResource(R.string.ob2_s2))
        Spacer(Modifier.height(12.dp))
        HowCard(Ic.Power, AccentGreen, stringResource(R.string.ob2_s3))
    }
}

@Composable
private fun HowCard(ic: Ic, tint: Color, text: String) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ic, tint, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            AppText(text, modifier = Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun PermissionStep() {
    val c = LocalAppColors.current
    StepScaffold {
        AppText(
            stringResource(R.string.ob3_title),
            size = 28.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        AppText(
            stringResource(R.string.ob3_intro),
            size = 14.sp,
            color = c.muted,
            align = TextAlign.Center,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(18.dp))
        PermissionCard(
            ic = Ic.Shield,
            tint = BrandBlue,
            title = stringResource(R.string.perm_vpn_title),
            why = stringResource(R.string.perm_vpn_why),
            never = stringResource(R.string.perm_vpn_never),
            note = stringResource(R.string.perm_vpn_when)
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            ic = Ic.Bell,
            tint = AccentAmber,
            title = stringResource(R.string.perm_notif_title),
            why = stringResource(R.string.perm_notif_why),
            never = stringResource(R.string.perm_notif_never),
            note = null
        )
    }
}

@Composable
private fun PermissionCard(ic: Ic, tint: Color, title: String, why: String, never: String, note: String?) {
    val c = LocalAppColors.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ic, tint)
            Spacer(Modifier.width(14.dp))
            AppText(title, size = 17.sp, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        AppText(stringResource(R.string.perm_why_label), size = 12.sp, weight = FontWeight.SemiBold, color = tint)
        AppText(why, size = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(10.dp))
        AppText(stringResource(R.string.perm_never_label), size = 12.sp, weight = FontWeight.SemiBold, color = AccentGreen)
        AppText(never, size = 14.sp, lineHeight = 21.sp)
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            AppText(note, size = 12.sp, color = c.muted, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ReadyStep() {
    val c = LocalAppColors.current
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    StepScaffold {
        Box(
            Modifier
                .size(120.dp)
                .scale(pop.value)
                .clip(CircleShape)
                .background(GreenGradient),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(Ic.Check, Color.White, size = 60.dp)
        }
        Spacer(Modifier.height(30.dp))
        AppText(
            stringResource(R.string.ob4_title),
            size = 28.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        AppText(
            stringResource(R.string.ob4_body),
            size = 16.sp,
            color = c.muted,
            align = TextAlign.Center,
            lineHeight = 25.sp
        )
    }
}
