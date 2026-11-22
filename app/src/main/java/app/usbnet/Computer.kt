package app.usbnet

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Starts the system download of the Windows installer. Returns false if no link is configured. */
fun downloadPcInstaller(ctx: Context): Boolean {
    if (Brand.PC_DOWNLOAD_URL.isEmpty()) return false
    return try {
        val req = DownloadManager.Request(Uri.parse(Brand.PC_DOWNLOAD_URL))
            .setTitle(Brand.PC_INSTALLER_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, Brand.PC_INSTALLER_NAME)
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        true
    } catch (e: Exception) {
        openUrl(ctx, Brand.PC_DOWNLOAD_URL)
        true
    }
}

private class Req(val titleRes: Int, val subRes: Int, val url: String)

/** "Computer" part of the guide: download the Windows app and its prerequisites, then the steps. */
@Composable
fun ComputerSection() {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    val hasLink = Brand.PC_DOWNLOAD_URL.isNotEmpty()
    val toastStarted = stringResource(R.string.pc_dl_started)
    val toastCopied = stringResource(R.string.pc_link_copied)
    val shareText = stringResource(R.string.pc_share_text, Brand.PC_DOWNLOAD_URL)

    val reqs = listOf(
        Req(R.string.req_win_t, R.string.req_win_s, ""),
        Req(R.string.req_net_t, R.string.req_net_s, Brand.URL_DOTNET),
        Req(R.string.req_vc_t, R.string.req_vc_s, Brand.URL_VCREDIST),
        Req(R.string.req_usb_t, R.string.req_usb_s, Brand.URL_USB_DRIVER),
        Req(R.string.req_cable_t, R.string.req_cable_s, ""),
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Ic.Download, BrandBlue)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AppText(stringResource(R.string.pc_dl_title), size = 17.sp, weight = FontWeight.Bold)
                    AppText(stringResource(R.string.pc_dl_sub), size = 12.sp, color = c.muted, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = stringResource(R.string.pc_dl_btn),
                onClick = {
                    if (downloadPcInstaller(ctx)) Toast.makeText(ctx, toastStarted, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasLink
            )
            if (!hasLink) {
                Spacer(Modifier.height(8.dp))
                AppText(stringResource(R.string.pc_dl_soon), size = 12.sp, color = AccentAmber, lineHeight = 18.sp)
            } else {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(stringResource(R.string.pc_copy), {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", Brand.PC_DOWNLOAD_URL))
                        Toast.makeText(ctx, toastCopied, Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f))
                    GhostButton(stringResource(R.string.pc_share), {
                        safeStart(
                            ctx,
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText),
                                null
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                AppText(stringResource(R.string.pc_dl_tip), size = 12.sp, color = c.muted, lineHeight = 18.sp)
            }
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Ic.Computer, AccentCyan)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AppText(stringResource(R.string.req_title), size = 17.sp, weight = FontWeight.Bold)
                    AppText(stringResource(R.string.req_sub), size = 12.sp, color = c.muted, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            reqs.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppText(stringResource(r.titleRes), size = 14.sp, weight = FontWeight.SemiBold)
                        AppText(stringResource(r.subRes), size = 12.sp, color = c.muted, lineHeight = 17.sp)
                    }
                    if (r.url.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        ChipButton(stringResource(R.string.req_get), true) { openUrl(ctx, r.url) }
                    }
                }
            }
            AppText(stringResource(R.string.req_note), size = 12.sp, color = c.muted, lineHeight = 18.sp)
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Ic.Screen, BrandBlue)
                Spacer(Modifier.width(14.dp))
                AppText(stringResource(R.string.guide_pc_title), size = 17.sp, weight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            stringArrayResource(R.array.g_pc).forEachIndexed { i, text -> StepItem(i + 1, text) }
        }
    }
}
