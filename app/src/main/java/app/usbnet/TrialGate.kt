package app.usbnet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    val busy = st.status == TrialStatus.Checking
    val needBazaar = st.status == TrialStatus.NeedBazaar
    val retryOnly = st.offline
    val btnText = stringResource(
        when {
            needBazaar -> R.string.gate_install
            retryOnly || st.error -> R.string.gate_retry
            else -> R.string.gate_login
        }
    )
    val bodyRes = when {
        needBazaar -> R.string.gate_need_bazaar
        busy -> R.string.gate_checking
        st.offline -> R.string.gate_offline
        st.error -> R.string.gate_error
        else -> R.string.gate_body
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(c.bg, if (c.dark) Color(0xFF111A3A) else Color(0xFFE8EDFC))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoMark(size = 96.dp)
            Spacer(Modifier.height(28.dp))
            AppText(
                stringResource(R.string.gate_title),
                size = 26.sp, weight = FontWeight.Bold, align = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            AppText(
                stringResource(bodyRes),
                size = 15.sp, color = c.muted, align = TextAlign.Center, lineHeight = 24.sp
            )
        }
        // One single button, pinned to the bottom (hidden while the check is running).
        if (!busy) {
            PrimaryButton(
                btnText,
                {
                    when {
                        needBazaar -> BazaarClientProxy.showInstallBazaarView(ctx)
                        retryOnly -> act?.let { Trial.refresh(it) }
                        else -> {
                            val i = Trial.signInIntent(ctx)
                            if (i != null) launcher.launch(i) else act?.let { Trial.refresh(it) }
                        }
                    }
                },
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            )
        }
    }
}
