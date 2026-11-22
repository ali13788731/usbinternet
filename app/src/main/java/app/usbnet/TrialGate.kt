package app.usbnet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farsitel.bazaar.BazaarClientProxy

/** Forced Bazaar sign-in shown on first launch (and whenever the account can't be verified). */
@Composable
fun BazaarGateScreen(onReady: () -> Unit) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val st by Trial.state.collectAsState()
    val act = ctx.findActivity()

    LaunchedEffect(st.status) {
        if (st.status == TrialStatus.Ready) onReady()
    }
    // No internet on a fresh install: keep trying quietly, the app continues by itself once it is online.
    LaunchedEffect(st.offline) {
        while (Trial.state.value.offline && !Trial.isReady) {
            delay(3000)
            act?.let { Trial.refresh(it) }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        act?.let { Trial.refresh(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            stringResource(R.string.gate_title),
            size = 24.sp, weight = FontWeight.Bold, align = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        AppText(
            stringResource(
                when (st.status) {
                    TrialStatus.NeedBazaar -> R.string.gate_need_bazaar
                    TrialStatus.Checking -> R.string.gate_checking
                    else -> if (st.offline) R.string.gate_offline else if (st.error) R.string.gate_error else R.string.gate_body
                }
            ),
            size = 14.sp, color = c.muted, align = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))
        when (st.status) {
            TrialStatus.NeedBazaar -> {
                PrimaryButton(stringResource(R.string.gate_install), { BazaarClientProxy.showInstallBazaarView(ctx) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                GhostButton(stringResource(R.string.gate_retry), { act?.let { Trial.refresh(it) } })
            }
            TrialStatus.Checking -> Unit
            else -> {
                PrimaryButton(
                    stringResource(R.string.gate_login),
                    {
                        val i = Trial.signInIntent(ctx)
                        if (i != null) launcher.launch(i) else act?.let { Trial.refresh(it) }
                    },
                    Modifier.fillMaxWidth()
                )
                if (st.error) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton(stringResource(R.string.gate_retry), { act?.let { Trial.refresh(it) } })
                    GhostButton(stringResource(R.string.gate_update), { BazaarClientProxy.showUpdateBazaarView(ctx) })
                }
            }
        }
    }
}
