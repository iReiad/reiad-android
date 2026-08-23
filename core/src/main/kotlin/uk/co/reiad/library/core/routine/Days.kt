package uk.co.reiad.library.core.routine

/* ============================================================
   Dates, the way the site does them.

   Every date in the routine tool is an ISO day, `2026-08-22`, and
   every walk over them on the site is `new Date(...T12:00:00Z)`
   followed by `setUTCDate`.

   ---- why this is arithmetic and not java.time ----

   Because it has to agree with JavaScript, including where
   JavaScript is odd. `setUTCMonth` OVERFLOWS rather than clamping:
   the 31st of March minus one month is the 3rd of March, not the
   28th of February, and `java.time` would give the clamped answer
   and be right about calendars and wrong about this. The fixture
   holds that case.

   Midday rather than midnight is the site's own guard, and it is
   why none of this needs a timezone: starting halfway through a
   day means no arithmetic here can land on the far side of a
   boundary.
   ============================================================ */

/** Days since 1970-01-01, from an ISO day. Howard Hinnant's civil
    algorithm, which is the one every standard library uses. */
internal fun epochDay(iso: String): Long {
    val y = iso.substring(0, 4).toLong()
    val m = iso.substring(5, 7).toLong()
    val d = iso.substring(8, 10).toLong()
    return epochDayOf(y, m, d)
}

internal fun epochDayOf(year: Long, month: Long, day: Long): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** And back. */
internal fun isoOf(days: Long): String {
    var z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    z = 0
    return buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(m.toString().padStart(2, '0'))
        append('-')
        append(d.toString().padStart(2, '0'))
    }
}

/** `back` days before `today`.

    Public because the app asks for a year of days before it can
    draw any of this: `runs` looks back 365 and a fetch that asked
    for less would draw a shorter history as a worse one. */
fun dayBefore(today: String, back: Int): String =
    isoOf(epochDay(today) - back)

/** The `days` days ending on `today`, OLDEST FIRST.

    Oldest first everywhere, because these arrays are drawn left
    to right and a reversed one is a chart that reads backwards
    without ever looking broken. */
fun walkBack(today: String, days: Int): List<String> =
    (days - 1 downTo 0).map { dayBefore(today, it) }

/** Sunday is 0, matching `Date.getUTCDay()`.

    1970-01-01 was a Thursday, which is the 4 below. */
fun weekdayOf(iso: String): Int {
    val e = epochDay(iso)
    return (((e + 4) % 7) + 7).toInt() % 7
}

/**
 * `delta` months from an ISO day, OVERFLOWING the way
 * `Date.setUTCMonth` does.
 *
 * The 31st of March minus one month is the 3rd of March, because
 * February has no 31st and JavaScript rolls forward rather than
 * clamping back. Every calendar library gives the 28th, and every
 * one of them would be a different answer from the site's for the
 * same reader on the same day.
 */
fun shiftMonths(iso: String, delta: Int): String {
    val year = iso.substring(0, 4).toLong()
    val month = iso.substring(5, 7).toLong()
    val day = iso.substring(8, 10).toLong()

    val total = year * 12 + (month - 1) + delta
    val toYear = Math.floorDiv(total, 12L)
    val toMonth = Math.floorMod(total, 12L) + 1
    /* The first of the target month plus the day offset, which is
       exactly what overflowing means: day 31 of a 28-day month is
       three days past the end of it. */
    return isoOf(epochDayOf(toYear, toMonth, 1) + (day - 1))
}
