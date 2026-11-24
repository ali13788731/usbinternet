package app.usbnet

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab { Home, Pro, Guide, Settings }

private enum class Phase { Splash, Login, Onboarding, Main }

/** Root of the UI: splash -> (first run) onboarding -> main app with the floating menu. */
@Composable
fun UsbNetApp(
    settings: SettingsState,
    startInMain: Boolean,
    route: String?,
    onRouteConsumed: () -> Unit,
    onRecreate: () -> Unit,
) {
    val ctx = LocalContext.current
    var phase by remember {
        mutableStateOf(
            when {
                !startInMain -> Phase.Splash
                !Trial.isReady -> Phase.Login
                else -> Phase.Main
            }
        )
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(350)) },
        label = "phase"
    ) { p ->
        when (p) {
            Phase.Splash -> SplashScreen(appVersionName(ctx)) {
                phase = when {
                    !Trial.isReady -> Phase.Login
                    settings.onboardingDone -> Phase.Main
                    else -> Phase.Onboarding
                }
            }
            Phase.Login -> BazaarGateScreen {
                phase = if (settings.onboardingDone) Phase.Main else Phase.Onboarding
            }
            Phase.Onboarding -> OnboardingScreen(onDone = {
                settings.updateOnboardingDone(true)
                phase = Phase.Main
            })
            Phase.Main -> MainShell(
                settings = settings,
                onRecreate = onRecreate,
                onReplayIntro = { phase = Phase.Onboarding },
                route = route,
                onRouteConsumed = onRouteConsumed
            )
        }
    }
}

@Composable
private fun MainShell(
    settings: SettingsState,
    onRecreate: () -> Unit,
    onReplayIntro: () -> Unit,
    route: String?,
    onRouteConsumed: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    val snap by TunnelMonitor.snapshot.collectAsState()
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val tab = Tab.values()[tabIndex.coerceIn(0, Tab.values().size - 1)]
    val setTab: (Tab) -> Unit = { tabIndex = it.ordinal }
    var showVpnExplain by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(0) }
    var paywall by remember { mutableStateOf<Int?>(null) }
    var updateDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var pendingOnly by remember { mutableStateOf(false) }
    val upd by Updates.info.collectAsState()
    val ent by Billing.state.collectAsState()
    val reason by TunnelMonitor.stopReason.collectAsState()
    val forced = upd != null && Updates.required(ctx)

    // Subscription is verified with Cafe Bazaar every time the app comes to the front.
    LaunchedEffect(Unit) {
        Updates.checkAsync(ctx) {}
    }
    LaunchedEffect(upd) {
        val i = upd
        if (i != null && Updates.consumePopup(ctx)) updateDialog = i
    }
    LaunchedEffect(route) {
        when (route) {
            Notif.ROUTE_PRO -> tabIndex = Tab.Pro.ordinal
            Notif.ROUTE_UPDATE -> upd?.let { updateDialog = it }
        }
        if (route != null) onRouteConsumed()
    }
    LaunchedEffect(reason) {
        if (reason == StopReason.Quota) paywall = R.string.pw_reason_quota
    }
    var pendingStart by remember { mutableStateOf(false) }

    val toastFailed = stringResource(R.string.vpn_failed)
    val toastReady = stringResource(R.string.toast_vpn_ready)

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (pendingStart) TunnelService.start(ctx, Prefs.resolveDnsList(ctx), pendingOnly)
        } else {
            Toast.makeText(ctx, toastFailed, Toast.LENGTH_SHORT).show()
        }
        pendingStart = false
    }

    val onToggle: () -> Unit = {
        if (snap.state == LinkState.Idle) {
            pendingOnly = false
            if (!ent.pro && Quota.state.value.usedUp) {
                paywall = R.string.pw_reason_quota
            } else if (VpnService.prepare(ctx) == null) {
                TunnelService.start(ctx, Prefs.resolveDnsList(ctx), pendingOnly)
            } else {
                pendingStart = true
                showVpnExplain = true
            }
        } else {
            TunnelService.stop(ctx)
        }
    }

    val onGrantVpn: () -> Unit = {
        if (VpnService.prepare(ctx) == null) {
            Toast.makeText(ctx, toastReady, Toast.LENGTH_SHORT).show()
        } else {
            pendingStart = false
            showVpnExplain = true
        }
    }

    // Keep the screen on while connected (if the user asked for it).
    val view = LocalView.current
    val keepOn = settings.keepScreenOn && snap.state == LinkState.Connected
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = tab != Tab.Home) {
        if (tab == Tab.Settings && settingsPage != 0) {
            settingsPage = if (settingsPage == SPage.ROUTING) SPage.CONNECTION else 0
        } else setTab(Tab.Home)
    }

    val bottomColor = if (c.dark) Color(0xFF0C1C1A) else Color(0xFFE3F0ED)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bg, bottomColor)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BrandBlue.copy(alpha = if (c.dark) 0.20f else 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f),
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.85f, 0f)
            )
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(150)) },
            label = "tabs"
        ) { t ->
            when (t) {
                Tab.Home -> HomeScreen(
                    snap = snap,
                    settings = settings,
                    onToggle = onToggle,
                    onOpenGuide = { setTab(Tab.Guide) },
                    onOpenPro = { setTab(Tab.Pro) }
                )
                Tab.Pro -> ProScreen()
                Tab.Guide -> GuideScreen()
                Tab.Settings -> SettingsScreen(
                    settings = settings,
                    connected = snap.state != LinkState.Idle,
                    page = settingsPage,
                    onPage = { settingsPage = it },
                    onOpenPro = { setTab(Tab.Pro) },
                    onPaywall = { paywall = it },
                    onLanguageChange = { lang ->
                        if (lang != settings.language) {
                            settings.updateLanguage(lang)
                            onRecreate()
                        }
                    },
                    onGrantVpn = onGrantVpn,
                    onReplayIntro = onReplayIntro,
                    onResetAll = {
                        settings.resetAll()
                        onRecreate()
                    }
                )
            }
        }

        // Content scrolls behind the system status bar (clock, Wi-Fi, signal...): fade it out under the bar.
        val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusH + 10.dp)
                .background(Brush.verticalGradient(listOf(c.bg, c.bg, Color.Transparent)))
        )

        FloatingNav(
            current = tab,
            onSelect = { setTab(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    if (forced) {
        ForceUpdateScreen(
            info = upd,
            connected = snap.state != LinkState.Idle,
            onUpdateOnlyConnect = {
                pendingOnly = true
                if (VpnService.prepare(ctx) == null) {
                    TunnelService.start(ctx, Prefs.resolveDnsList(ctx), true)
                    pendingOnly = false
                } else {
                    pendingStart = true
                    showVpnExplain = true
                }
            },
            onStop = { TunnelService.stop(ctx) }
        )
    }
    paywall?.let { r ->
        PaywallDialog(
            reason = r,
            onUpgrade = { paywall = null; setTab(Tab.Pro) },
            onDismiss = { paywall = null }
        )
    }
    updateDialog?.let { i ->
        if (!forced) UpdateDialog(i) { updateDialog = null }
    }

    if (showVpnExplain) {
        AppDialog(
            title = stringResource(R.string.vpn_explain_title),
            onDismiss = {
                showVpnExplain = false
                pendingStart = false
            },
            icon = Ic.Shield,
            primaryText = stringResource(R.string.continue_btn),
            onPrimary = {
                showVpnExplain = false
                val intent = VpnService.prepare(ctx)
                if (intent != null) {
                    vpnLauncher.launch(intent)
                } else {
                    if (pendingStart) TunnelService.start(ctx, Prefs.resolveDnsList(ctx), pendingOnly)
                    pendingStart = false
                }
            },
            secondaryText = stringResource(R.string.cancel_btn),
            onSecondary = {
                showVpnExplain = false
                pendingStart = false
            }
        ) {
            AppText(
                stringResource(R.string.vpn_explain_body),
                size = 14.sp,
                color = c.muted,
                lineHeight = 22.sp,
                align = TextAlign.Center
            )
        }
    }
}

private class NavEntry(val tab: Tab, val ic: Ic, val label: Int)

private val navEntries = listOf(
    NavEntry(Tab.Home, Ic.Home, R.string.nav_home),
    NavEntry(Tab.Pro, Ic.Crown, R.string.nav_pro),
    NavEntry(Tab.Guide, Ic.Guide, R.string.nav_guide),
    NavEntry(Tab.Settings, Ic.Settings, R.string.nav_settings),
)

/** Floating pill menu with an animated selection. */
@Composable
private fun FloatingNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .shadow(18.dp, shape)
            .background(c.surface.copy(alpha = 0.97f), shape)
            .border(1.dp, c.border, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        navEntries.forEach { entry ->
            val selected = entry.tab == current
            val fill by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(260),
                label = "navFill"
            )
            val iconColor by animateColorAsState(
                targetValue = if (selected) Color.White else c.muted,
                animationSpec = tween(260),
                label = "navIcon"
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(BrandGradient, RoundedCornerShape(26.dp), fill)
                    .bounceClick(true) { onSelect(entry.tab) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(entry.ic, iconColor, size = 22.dp)
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkHorizontally(tween(200)) + fadeOut(tween(150))
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        AppText(
                            stringResource(entry.label),
                            size = 13.sp,
                            weight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
