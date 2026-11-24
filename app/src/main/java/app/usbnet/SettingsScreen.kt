package app.usbnet

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

object SPage {
    const val HUB = 0
    const val GENERAL = 1
    const val CONNECTION = 2
    const val PERMISSIONS = 3
    const val ROUTING = 4
    const val ABOUT = 5
}

@Composable
private fun RowDivider() {
    val c = LocalAppColors.current
    Spacer(Modifier.height(4.dp))
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.border.copy(alpha = 0.6f))
    )
    Spacer(Modifier.height(4.dp))
}


@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun HubRow(ic: Ic, tint: Color, title: Int, sub: Int, onClick: () -> Unit, badge: String? = null) {
    SettingRow(
        ic = ic, tint = tint,
        title = stringResource(title), subtitle = stringResource(sub),
        onClick = onClick,
        trailing = {
            if (badge != null) {
                StatusChip(badge, AccentAmber)
                Spacer(Modifier.width(6.dp))
            }
            Chevron(LocalAppColors.current.muted)
        }
    )
}

@Composable
fun SettingsScreen(
    settings: SettingsState,
    connected: Boolean,
    page: Int,
    onPage: (Int) -> Unit,
    onLanguageChange: (String) -> Unit,
    onGrantVpn: () -> Unit,
    onReplayIntro: () -> Unit,
    onResetAll: () -> Unit,
    onOpenPro: () -> Unit,
    onPaywall: (Int) -> Unit,
) {
    when (page) {
        SPage.GENERAL -> GeneralPage(settings, onLanguageChange)
        SPage.CONNECTION -> ConnectionPage(settings, connected, onPage, onPaywall)
        SPage.PERMISSIONS -> PermissionsPage(onGrantVpn)
        SPage.ROUTING -> AppRoutingScreen(settings, connected)
        SPage.ABOUT -> AboutScreen(onReplayIntro = onReplayIntro)
        else -> HubPage(onPage, onOpenPro, onReplayIntro, onResetAll)
    }
}

@Composable
private fun HubPage(onPage: (Int) -> Unit, onOpenPro: () -> Unit, onReplayIntro: () -> Unit, onResetAll: () -> Unit) {
    val c = LocalAppColors.current
    val ent by Billing.state.collectAsState()
    var showReset by remember { mutableStateOf(false) }

    Page {
        Reveal(0) { ScreenHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_sub)) }
        Reveal(1) {
            AppCard {
                SettingRow(
                    ic = Ic.Crown, tint = AccentAmber,
                    title = stringResource(R.string.hub_sub_title),
                    subtitle = stringResource(if (ent.pro) R.string.pro_active else R.string.hub_sub_free),
                    onClick = onOpenPro,
                    trailing = { Chevron(c.muted) }
                )
            }
        }
        Reveal(2) {
            AppCard {
                HubRow(Ic.Settings, BrandBlue, R.string.hub_general, R.string.hub_general_sub, { onPage(SPage.GENERAL) })
                RowDivider()
                HubRow(Ic.Dns, AccentCyan, R.string.hub_connection, R.string.hub_connection_sub, { onPage(SPage.CONNECTION) })
                RowDivider()
                HubRow(Ic.Shield, AccentGreen, R.string.hub_permissions, R.string.hub_permissions_sub, { onPage(SPage.PERMISSIONS) })
                RowDivider()
                HubRow(Ic.About, BrandViolet, R.string.hub_about, R.string.hub_about_sub, { onPage(SPage.ABOUT) })
            }
        }
        Reveal(3) {
            AppCard {
                SettingRow(
                    ic = Ic.Play, tint = BrandBlue,
                    title = stringResource(R.string.other_intro_title),
                    subtitle = stringResource(R.string.other_intro_sub),
                    onClick = onReplayIntro,
                    trailing = { Chevron(c.muted) }
                )
                RowDivider()
                SettingRow(
                    ic = Ic.Reset, tint = AccentRed,
                    title = stringResource(R.string.other_reset_title),
                    subtitle = stringResource(R.string.other_reset_sub),
                    onClick = { showReset = true },
                    trailing = { Chevron(c.muted) }
                )
            }
        }
    }

    if (showReset) {
        AppDialog(
            title = stringResource(R.string.reset_confirm_title),
            onDismiss = { showReset = false },
            icon = Ic.Reset,
            iconTint = AccentRed,
            primaryText = stringResource(R.string.reset_confirm_yes),
            onPrimary = {
                showReset = false
                onResetAll()
            },
            secondaryText = stringResource(R.string.cancel_btn),
            onSecondary = { showReset = false }
        ) {
            AppText(
                stringResource(R.string.reset_confirm_body),
                size = 14.sp, color = c.muted, lineHeight = 22.sp, align = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun GeneralPage(settings: SettingsState, onLanguageChange: (String) -> Unit) {
    val langValues = listOf(Prefs.LANG_SYSTEM, Prefs.LANG_EN, Prefs.LANG_FA)
    val themeValues = listOf(Prefs.THEME_SYSTEM, Prefs.THEME_DARK, Prefs.THEME_LIGHT)
    Page {
        Reveal(0) { ScreenHeader(stringResource(R.string.hub_general), stringResource(R.string.hub_general_sub)) }
        Reveal(1) {
            AppCard {
                SettingRow(Ic.Globe, BrandBlue, stringResource(R.string.lang_title), stringResource(R.string.lang_sub))
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(
                        stringResource(R.string.lang_system), stringResource(R.string.lang_en), stringResource(R.string.lang_fa)
                    ),
                    selected = langValues.indexOf(settings.language).coerceAtLeast(0),
                    onSelect = { onLanguageChange(langValues[it]) }
                )
                RowDivider()
                SettingRow(Ic.Contrast, BrandViolet, stringResource(R.string.theme_title))
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(
                        stringResource(R.string.theme_system), stringResource(R.string.theme_dark), stringResource(R.string.theme_light)
                    ),
                    selected = themeValues.indexOf(settings.theme).coerceAtLeast(0),
                    onSelect = { settings.updateTheme(themeValues[it]) }
                )
            }
        }
        Reveal(2) {
            AppCard {
                ToggleRow(Ic.Pulse, BrandBlue, stringResource(R.string.graph_title), stringResource(R.string.graph_sub),
                    settings.showGraph, { settings.updateShowGraph(it) })
                RowDivider()
                ToggleRow(Ic.Vibrate, AccentAmber, stringResource(R.string.haptic_title), stringResource(R.string.haptic_sub),
                    settings.haptics, { settings.updateHaptics(it) })
                RowDivider()
                SettingRow(Ic.Speed, BrandViolet, stringResource(R.string.units_title))
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(stringResource(R.string.units_mbs), stringResource(R.string.units_mbps)),
                    selected = settings.speedUnit.coerceIn(0, 1),
                    onSelect = { settings.updateSpeedUnit(it) }
                )
            }
        }
    }
}

@Composable
private fun ConnectionPage(settings: SettingsState, connected: Boolean, onPage: (Int) -> Unit, onPaywall: (Int) -> Unit) {
    val c = LocalAppColors.current
    val ent by Billing.state.collectAsState()
    val dnsValues = listOf(Prefs.DNS_GOOGLE, Prefs.DNS_CLOUDFLARE, Prefs.DNS_QUAD9, Prefs.DNS_CUSTOM)
    val timerValues = listOf(0, 15, 30, 60, 120)
    val proBadge = stringResource(R.string.badge_pro)

    Page {
        Reveal(0) { ScreenHeader(stringResource(R.string.hub_connection), stringResource(R.string.hub_connection_sub)) }
        Reveal(1) {
            AppCard {
                SettingRow(Ic.Dns, AccentCyan, stringResource(R.string.dns_title), stringResource(R.string.dns_sub))
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(
                        stringResource(R.string.dns_google), stringResource(R.string.dns_cloudflare),
                        stringResource(R.string.dns_quad9), stringResource(R.string.dns_custom)
                    ),
                    selected = dnsValues.indexOf(settings.dnsChoice).coerceAtLeast(0),
                    onSelect = { settings.updateDnsChoice(dnsValues[it]) },
                    enabled = !connected
                )
                AnimatedVisibility(
                    visible = settings.dnsChoice == Prefs.DNS_CUSTOM,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        val invalid = settings.customDns.isNotEmpty() && !Prefs.isValidIpv4(settings.customDns)
                        OutlinedTextField(
                            value = settings.customDns,
                            onValueChange = { v ->
                                settings.updateCustomDns(v.filter { ch -> ch.isDigit() || ch == '.' }.take(15))
                            },
                            enabled = !connected,
                            singleLine = true,
                            isError = invalid,
                            label = { Text(stringResource(R.string.dns_custom_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (invalid) {
                            Spacer(Modifier.height(4.dp))
                            AppText(stringResource(R.string.dns_invalid), size = 12.sp, color = AccentRed)
                        }
                    }
                }
                if (connected) {
                    Spacer(Modifier.height(8.dp))
                    AppText(stringResource(R.string.dns_locked_hint), size = 12.sp, color = AccentAmber)
                }
                RowDivider()
                ToggleRow(Ic.Screen, AccentGreen, stringResource(R.string.keep_screen_title), stringResource(R.string.keep_screen_sub),
                    settings.keepScreenOn, { settings.updateKeepScreenOn(it) })
            }
        }
        // Pro features
        Reveal(2) {
            AppCard {
                SettingRow(
                    ic = Ic.Apps, tint = BrandViolet,
                    title = stringResource(R.string.route_title),
                    subtitle = stringResource(R.string.route_row_sub),
                    onClick = { if (ent.pro) onPage(SPage.ROUTING) else onPaywall(R.string.pw_reason_routing) },
                    trailing = {
                        if (!ent.pro) { StatusChip(proBadge, AccentAmber); Spacer(Modifier.width(6.dp)) }
                        Chevron(c.muted)
                    }
                )
                RowDivider()
                SettingRow(
                    ic = Ic.Clock, tint = AccentAmber,
                    title = stringResource(R.string.timer_title),
                    subtitle = stringResource(R.string.timer_sub),
                    trailing = { if (!ent.pro) StatusChip(proBadge, AccentAmber) }
                )
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = timerValues.map { if (it == 0) stringResource(R.string.timer_off) else stringResource(R.string.timer_min, it) },
                    selected = timerValues.indexOf(settings.autoStop).coerceAtLeast(0),
                    onSelect = {
                        if (ent.pro) settings.updateAutoStop(timerValues[it]) else onPaywall(R.string.pw_reason_timer)
                    },
                    enabled = !connected
                )
            }
        }
    }
}

@Composable
private fun PermissionsPage(onGrantVpn: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    val tick = rememberResumeTick()
    val vpnGranted = remember(tick) { VpnService.prepare(ctx) == null }
    val notifGranted = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Page {
        Reveal(0) { ScreenHeader(stringResource(R.string.hub_permissions), stringResource(R.string.hub_permissions_sub)) }
        Reveal(1) {
            AppCard {
                SettingRow(
                    ic = Ic.Shield, tint = BrandBlue,
                    title = stringResource(R.string.perm_vpn_title),
                    subtitle = stringResource(R.string.perm_vpn_set_sub),
                    trailing = {
                        if (vpnGranted) StatusChip(stringResource(R.string.perm_status_granted), AccentGreen)
                        else ChipButton(stringResource(R.string.perm_grant), true) { onGrantVpn() }
                    }
                )
                if (!vpnGranted) {
                    AppText(stringResource(R.string.perm_vpn_never), size = 12.sp, color = c.muted, lineHeight = 17.sp)
                }
                RowDivider()
                SettingRow(
                    ic = Ic.Bell, tint = AccentAmber,
                    title = stringResource(R.string.perm_notif_title),
                    subtitle = stringResource(R.string.perm_notif_set_sub),
                    trailing = {
                        if (notifGranted) StatusChip(stringResource(R.string.perm_status_granted), AccentGreen)
                        else ChipButton(stringResource(R.string.perm_grant), true) {
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else safeStart(
                                ctx,
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            )
                        }
                    }
                )
                if (!notifGranted) {
                    AppText(stringResource(R.string.perm_notif_never), size = 12.sp, color = c.muted, lineHeight = 17.sp)
                }
            }
        }
        Reveal(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.set_group_system))
                AppCard {
                    ShortcutRow(Ic.Lock, AccentCyan, R.string.sys_vpn_title, R.string.sys_vpn_sub) {
                        safeStart(ctx, Intent(Settings.ACTION_VPN_SETTINGS))
                    }
                    RowDivider()
                    ShortcutRow(Ic.Bell, AccentAmber, R.string.sys_notif_title, R.string.sys_notif_sub) {
                        safeStart(
                            ctx,
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                    RowDivider()
                    ShortcutRow(Ic.Battery, AccentGreen, R.string.sys_battery_title, R.string.sys_battery_sub) {
                        safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                    RowDivider()
                    ShortcutRow(Ic.Dev, BrandViolet, R.string.sys_dev_title, R.string.sys_dev_sub) {
                        openDeveloperSettings(ctx)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(ic: Ic, tint: Color, title: Int, sub: Int, onClick: () -> Unit) {
    SettingRow(
        ic = ic, tint = tint,
        title = stringResource(title), subtitle = stringResource(sub),
        onClick = onClick,
        trailing = { Chevron(LocalAppColors.current.muted) }
    )
}
