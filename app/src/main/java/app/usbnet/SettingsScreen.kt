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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

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
fun SettingsScreen(
    settings: SettingsState,
    connected: Boolean,
    onLanguageChange: (String) -> Unit,
    onGrantVpn: () -> Unit,
    onReplayIntro: () -> Unit,
    onResetAll: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    val tick = rememberResumeTick()
    var showReset by remember { mutableStateOf(false) }

    val vpnGranted = remember(tick) { VpnService.prepare(ctx) == null }
    val notifGranted = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val langValues = listOf(Prefs.LANG_SYSTEM, Prefs.LANG_EN, Prefs.LANG_FA)
    val themeValues = listOf(Prefs.THEME_SYSTEM, Prefs.THEME_DARK, Prefs.THEME_LIGHT)
    val dnsValues = listOf(Prefs.DNS_GOOGLE, Prefs.DNS_CLOUDFLARE, Prefs.DNS_QUAD9, Prefs.DNS_CUSTOM)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Reveal(0) {
            ScreenHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_sub))
        }

        // ---- Language
        Reveal(1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.set_group_language))
                AppCard {
                    SettingRow(
                        ic = Ic.Globe,
                        tint = BrandBlue,
                        title = stringResource(R.string.lang_title),
                        subtitle = stringResource(R.string.lang_sub)
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        options = listOf(
                            stringResource(R.string.lang_system),
                            stringResource(R.string.lang_en),
                            stringResource(R.string.lang_fa)
                        ),
                        selected = langValues.indexOf(settings.language).coerceAtLeast(0),
                        onSelect = { onLanguageChange(langValues[it]) }
                    )
                }
            }
        }

        // ---- Appearance
        Reveal(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.set_group_appearance))
                AppCard {
                    SettingRow(
                        ic = Ic.Contrast,
                        tint = BrandViolet,
                        title = stringResource(R.string.theme_title)
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        options = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_dark),
                            stringResource(R.string.theme_light)
                        ),
                        selected = themeValues.indexOf(settings.theme).coerceAtLeast(0),
                        onSelect = { settings.updateTheme(themeValues[it]) }
                    )
                }
            }
        }

        // ---- Connection
        Reveal(3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.set_group_connection))
                AppCard {
                    SettingRow(
                        ic = Ic.Dns,
                        tint = AccentCyan,
                        title = stringResource(R.string.dns_title),
                        subtitle = stringResource(R.string.dns_sub)
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        options = listOf(
                            stringResource(R.string.dns_google),
                            stringResource(R.string.dns_cloudflare),
                            stringResource(R.string.dns_quad9),
                            stringResource(R.string.dns_custom)
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
                    ToggleRow(
                        ic = Ic.Screen,
                        tint = AccentGreen,
                        title = stringResource(R.string.keep_screen_title),
                        subtitle = stringResource(R.string.keep_screen_sub),
                        checked = settings.keepScreenOn,
                        onChange = { settings.updateKeepScreenOn(it) }
                    )
                    RowDivider()
                    ToggleRow(
                        ic = Ic.Vibrate,
                        tint = AccentAmber,
                        title = stringResource(R.string.haptic_title),
                        subtitle = stringResource(R.string.haptic_sub),
                        checked = settings.haptics,
                        onChange = { settings.updateHaptics(it) }
                    )
                    RowDivider()
                    ToggleRow(
                        ic = Ic.Pulse,
                        tint = BrandBlue,
                        title = stringResource(R.string.graph_title),
                        subtitle = stringResource(R.string.graph_sub),
                        checked = settings.showGraph,
                        onChange = { settings.updateShowGraph(it) }
                    )
                    RowDivider()
                    SettingRow(
                        ic = Ic.Speed,
                        tint = BrandViolet,
                        title = stringResource(R.string.units_title)
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        options = listOf(
                            stringResource(R.string.units_mbs),
                            stringResource(R.string.units_mbps)
                        ),
                        selected = settings.speedUnit.coerceIn(0, 1),
                        onSelect = { settings.updateSpeedUnit(it) }
                    )
                }
            }
        }

        // ---- Permissions
        Reveal(4) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.set_group_permissions))
                AppCard {
                    SettingRow(
                        ic = Ic.Shield,
                        tint = BrandBlue,
                        title = stringResource(R.string.perm_vpn_title),
                        subtitle = stringResource(R.string.perm_vpn_set_sub),
                        trailing = {
                            if (vpnGranted) {
                                StatusChip(stringResource(R.string.perm_status_granted), AccentGreen)
                            } else {
                                ChipButton(stringResource(R.string.perm_grant), true) { onGrantVpn() }
                            }
                        }
                    )
                    if (!vpnGranted) {
                        AppText(
                            stringResource(R.string.perm_vpn_never),
                            size = 12.sp,
                            color = c.muted,
                            lineHeight = 17.sp
                        )
                    }
                    RowDivider()
                    SettingRow(
                        ic = Ic.Bell,
                        tint = AccentAmber,
                        title = stringResource(R.string.perm_notif_title),
                        subtitle = stringResource(R.string.perm_notif_set_sub),
                        trailing = {
                            if (notifGranted) {
                                StatusChip(stringResource(R.string.perm_status_granted), AccentGreen)
                            } else {
                                ChipButton(stringResource(R.string.perm_grant), true) {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        safeStart(
                                            ctx,
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    if (!notifGranted) {
                        AppText(
                            stringResource(R.string.perm_notif_never),
                            size = 12.sp,
                            color = c.muted,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // ---- System shortcuts
        Reveal(5) {
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
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
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

        // ---- Other
        Reveal(6) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.set_group_other))
                AppCard {
                    ShortcutRow(Ic.Play, BrandBlue, R.string.other_intro_title, R.string.other_intro_sub) {
                        onReplayIntro()
                    }
                    RowDivider()
                    ShortcutRow(Ic.Reset, AccentRed, R.string.other_reset_title, R.string.other_reset_sub) {
                        showReset = true
                    }
                }
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = {
                AppText(stringResource(R.string.reset_confirm_title), size = 20.sp, weight = FontWeight.Bold)
            },
            text = {
                AppText(stringResource(R.string.reset_confirm_body), size = 14.sp, color = c.muted, lineHeight = 21.sp)
            },
            confirmButton = {
                ChipButton(stringResource(R.string.reset_confirm_yes), true) {
                    showReset = false
                    onResetAll()
                }
            },
            dismissButton = {
                GhostButton(stringResource(R.string.cancel_btn), { showReset = false })
            },
            containerColor = c.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ShortcutRow(ic: Ic, tint: androidx.compose.ui.graphics.Color, title: Int, sub: Int, onClick: () -> Unit) {
    SettingRow(
        ic = ic,
        tint = tint,
        title = stringResource(title),
        subtitle = stringResource(sub),
        onClick = onClick,
        trailing = {
            AppText(
                stringResource(R.string.open_btn),
                size = 13.sp,
                weight = FontWeight.SemiBold,
                color = BrandBlue
            )
        }
    )
}
