package app.usbnet

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** All notifications except the ongoing tunnel one. Texts follow the in-app language. */
object Notif {
    const val EXTRA_ROUTE = "route"
    const val ROUTE_PRO = "pro"
    const val ROUTE_UPDATE = "update"

    const val CH_STATUS = "tunnel"
    const val CH_USAGE = "usage"
    const val CH_PROMO = "promo"
    const val CH_UPDATE = "update"

    private const val ID_USAGE = 11
    private const val ID_ALERT = 12
    private const val ID_PROMO = 13
    private const val ID_UPDATE = 14

    private fun fa(c: Context) = Prefs.effectiveLanguage(c) == "fa"
    private fun t(c: Context, f: String, e: String) = if (fa(c)) f else e

    fun ensureChannels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, t(c, "وضعیت اتصال", "Connection status"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_USAGE, t(c, "حجم مصرفی", "Data usage"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_PROMO, t(c, "پیشنهاد ارتقا", "Upgrade offers"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_UPDATE, t(c, "به‌روزرسانی", "Updates"), NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    fun contentIntent(c: Context, route: String?, req: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (route != null) i.putExtra(EXTRA_ROUTE, route)
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun post(c: Context, id: Int, n: android.app.Notification) {
        if (!canPost(c)) return
        try {
            ensureChannels(c)
            c.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: Exception) {
        }
    }

    private fun base(c: Context, ch: String) = NotificationCompat.Builder(c, ch)
        .setSmallIcon(R.drawable.ic_stat_net)
        .setAutoCancel(true)

    /** Shown when the app is opened: how much of the free allowance is used (Pro: a thank-you). */
    fun launch(ctx: Context) {
        val c = LocaleHelper.wrap(ctx)
        val pro = Billing.state.value.pro
        val q = Quota.state.value
        val title: String
        val text: String
        if (pro) {
            title = t(c, "اشتراک پرو فعال است", "Pro is active")
            text = t(c, "حجم نامحدود. از پشتیبانی شما سپاسگزاریم.", "Unlimited data. Thank you for your support.")
        } else {
            title = t(c, "حجم رایگان آزمایشی", "Free trial data")
            text = t(
                c,
                "${formatBytes(q.usedFree)} از ${formatBytes(q.limit)} مصرف شده؛ ${formatBytes(q.remaining)} باقی مانده.",
                "${formatBytes(q.usedFree)} of ${formatBytes(q.limit)} used; ${formatBytes(q.remaining)} left."
            )
        }
        post(
            c, ID_USAGE,
            base(c, CH_USAGE).setContentTitle(title).setContentText(text)
                .setTimeoutAfter(30_000)
                .setContentIntent(contentIntent(c, if (pro) null else ROUTE_PRO, 21)).build()
        )
    }

    /** level 80 or 100. */
    fun quotaAlert(ctx: Context, level: Int) {
        val c = LocaleHelper.wrap(ctx)
        val title = if (level >= 100) t(c, "حجم رایگان تمام شد", "Free data used up")
        else t(c, "۸۰٪ حجم رایگان مصرف شد", "80% of free data used")
        val text = if (level >= 100)
            t(c, "اتصال قطع شد. با اشتراک پرو بدون محدودیت ادامه دهید.", "The connection was stopped. Go Pro to continue without limits.")
        else t(c, "کمی از حجم آزمایشی مانده است. با پرو نامحدود شوید.", "You are close to the end of the free trial. Go Pro for unlimited use.")
        post(
            c, ID_ALERT,
            base(c, CH_PROMO).setContentTitle(title).setContentText(text)
                .setContentIntent(contentIntent(c, ROUTE_PRO, 22)).build()
        )
    }

    fun updateBlocked(ctx: Context) {
        val c = LocaleHelper.wrap(ctx)
        post(
            c, ID_ALERT,
            base(c, CH_UPDATE)
                .setContentTitle(t(c, "نسخه جدید لازم است", "Update required"))
                .setContentText(t(c, "برای ادامه، برنامه را به‌روز کنید.", "Update the app to keep using it."))
                .setContentIntent(contentIntent(c, ROUTE_UPDATE, 23)).build()
        )
    }

    fun update(ctx: Context, info: UpdateInfo, mandatory: Boolean) {
        val c = LocaleHelper.wrap(ctx)
        val notes = info.notes(fa(c))
        val title = if (mandatory) t(c, "به‌روزرسانی اجباری ${info.name}", "Required update ${info.name}")
        else t(c, "نسخه جدید ${info.name}", "New version ${info.name}")
        val body = if (notes.isEmpty()) t(c, "برای دریافت نسخه جدید وارد شوید.", "Tap to get the new version.")
        else notes.joinToString("\n") { "• $it" }
        post(
            c, ID_UPDATE,
            base(c, CH_UPDATE).setContentTitle(title).setContentText(notes.firstOrNull() ?: body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent(c, ROUTE_UPDATE, 24)).build()
        )
    }

    // ---- periodic upgrade nudges ----

    private const val NUDGE_PREFS = "nudge"

    fun schedule(ctx: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<NudgeWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("nudge", ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (e: Exception) {
        }
    }

    /** Called by the worker. Free users get a rotating feature reminder every few days. */
    fun nudge(ctx: Context) {
        val c = LocaleHelper.wrap(ctx)
        if (Billing.cachedPro(c)) return
        val sp = c.getSharedPreferences(NUDGE_PREFS, Context.MODE_PRIVATE)
        val expired = Billing.everSubscribed(c)
        val gapDays = if (expired) 3L else 2L
        val last = sp.getLong("last", 0L)
        val now = System.currentTimeMillis()
        if (last == 0L) { // first run: wait one gap, don't nag right after install
            sp.edit().putLong("last", now).apply()
            return
        }
        if (now - last < gapDays * 24L * 3600L * 1000L) return
        val i = sp.getInt("i", 0)
        sp.edit().putLong("last", now).putInt("i", i + 1).apply()

        val feats = listOf(
            t(c, "حجم نامحدود، بدون سقف ۳۰۰ مگابایتی", "Unlimited data, no 300 MB cap"),
            t(c, "انتخاب برنامه‌هایی که از کابل استفاده می‌کنند", "Choose which apps use the cable"),
            t(c, "قطع خودکار با تایمر و گزارش مصرف روزانه", "Auto-stop timer and daily usage report"),
            t(c, "اجرای خودکار نسخه کامپیوتر با ویندوز", "PC app starts with Windows"),
        )
        val title = if (expired) t(c, "اشتراک پرو شما تمام شده است", "Your Pro subscription has ended")
        else t(c, "با پرو بدون محدودیت وصل شوید", "Go Pro, connect without limits")
        val text = feats[i % feats.size]
        val big = feats.joinToString("\n") { "• $it" }
        post(
            c, ID_PROMO,
            base(c, CH_PROMO).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(big))
                .setContentIntent(contentIntent(c, ROUTE_PRO, 25)).build()
        )
    }
}

class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            // Updates first (notifies once per version), then the upgrade nudge.
            Updates.init(applicationContext)
            Updates.fetchBlocking(applicationContext)
            Notif.nudge(applicationContext)
        } catch (e: Exception) {
        }
        return Result.success()
    }
}
