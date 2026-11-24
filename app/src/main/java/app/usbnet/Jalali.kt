package app.usbnet

import java.util.Calendar
import java.util.Locale

/** Persian (Jalali) calendar helpers. The free data allowance renews on the 1st of every Persian month. */
object Jalali {
    private val G_DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val J_DAYS = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    private val MONTHS_FA = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** Returns [year, month 1..12, day 1..31]. */
    fun fromGregorian(gy: Int, gm: Int, gd: Int): IntArray {
        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += G_DAYS[i]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo += 1
        gDayNo += gd2
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        var i = 0
        while (i < 11 && jDayNo >= J_DAYS[i]) {
            jDayNo -= J_DAYS[i]
            i++
        }
        return intArrayOf(jy, i + 1, jDayNo + 1)
    }

    fun fromMillis(ms: Long): IntArray {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** "1405-08" */
    fun periodKey(ms: Long): String {
        val j = fromMillis(ms)
        return String.format(Locale.US, "%04d-%02d", j[0], j[1])
    }

    fun format(ms: Long): String {
        val j = fromMillis(ms)
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
    }

    fun formatGregorian(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Date in the calendar that matches the UI language. */
    fun dateLabel(ms: Long, fa: Boolean): String = if (fa) format(ms) else formatGregorian(ms)

    /** "1405-08" -> "آبان 1405" (fa) or "1405/08" (en). */
    fun monthLabel(period: String, fa: Boolean): String {
        val parts = period.split("-")
        if (parts.size != 2) return period
        val m = parts[1].toIntOrNull() ?: return period
        return if (fa && m in 1..12) "${MONTHS_FA[m - 1]} ${parts[0]}" else "${parts[0]}/${parts[1]}"
    }

    /** Days (1..31) until the next Persian month starts. */
    fun daysUntilRenewal(ms: Long): Int {
        val start = periodKey(ms)
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        for (d in 1..32) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            if (periodKey(c.timeInMillis) != start) return d
        }
        return 30
    }
}
