package app.usbnet

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppRow(val pkg: String, val label: String)

/** Pro: choose which apps use the cable (only these / everything except these). */
@Composable
fun AppRoutingScreen(settings: SettingsState, connected: Boolean) {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    var apps by remember { mutableStateOf<List<AppRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            val out = ArrayList<AppRow>()
            for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                val p = ri.activityInfo.packageName
                if (p == ctx.packageName || p == Brand.BAZAAR_PACKAGE || !seen.add(p)) continue
                out.add(AppRow(p, ri.loadLabel(pm).toString()))
            }
            out.sortedBy { it.label.lowercase() }
        }
        loading = false
    }

    val modes = listOf(
        stringResource(R.string.route_all),
        stringResource(R.string.route_only),
        stringResource(R.string.route_except)
    )
    val filtered = if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScreenHeader(stringResource(R.string.route_title), stringResource(R.string.route_sub))
                Segmented(modes, settings.routeMode.coerceIn(0, 2), { settings.updateRouteMode(it) }, enabled = !connected)
                if (connected) AppText(stringResource(R.string.route_locked), size = 12.sp, color = AccentAmber)
                if (settings.routeMode != Prefs.ROUTE_ALL) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { androidx.compose.material3.Text(stringResource(R.string.route_search)) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppText(
                        stringResource(R.string.route_selected, settings.routeApps.size),
                        size = 12.sp, color = c.muted
                    )
                }
                if (loading) AppText(stringResource(R.string.route_loading), size = 13.sp, color = c.muted)
            }
        }
        if (settings.routeMode != Prefs.ROUTE_ALL) {
            items(filtered, key = { it.pkg }) { a ->
                val on = a.pkg in settings.routeApps
                AppCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText(a.label, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            AppText(a.pkg, size = 11.sp, color = c.muted, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        AppSwitch(on, { v ->
                            val s = HashSet(settings.routeApps)
                            if (v) s.add(a.pkg) else s.remove(a.pkg)
                            settings.updateRouteApps(s)
                        }, enabled = !connected)
                    }
                }
            }
        }
    }
}
