package com.personal.weather.forecast

import java.time.Duration
import java.time.Instant
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * When the sun is down, from the NOAA solar position equations (good to about a minute below the polar circles).
 * Night is the sun's upper edge below the horizon, allowing for refraction: the same sunrise/sunset an almanac gives.
 */
object SunTimes {
    data class Night(val start: Instant, val end: Instant)

    private const val HORIZON_DEG = -0.833
    private val STEP = Duration.ofMinutes(10)

    /** The parts of [start, end) when the sun is down. Empty under the midnight sun; the whole span in polar night. */
    fun nights(start: Instant, end: Instant, lat: Double, lon: Double): List<Night> {
        val dark = { t: Instant -> elevationDeg(t, lat, lon) < HORIZON_DEG }
        val nights = mutableListOf<Night>()
        var nightStart: Instant? = start.takeIf(dark)
        var t = start
        var tDark = nightStart != null
        while (t < end) {
            val next = minOf(t.plus(STEP), end)
            val nextDark = dark(next)
            if (nextDark != tDark) {
                val edge = edge(t, next, dark)
                if (nextDark) {
                    nightStart = edge
                } else {
                    nights += Night(nightStart ?: start, edge)
                    nightStart = null
                }
            }
            t = next
            tDark = nextDark
        }
        nightStart?.let { nights += Night(it, end) }
        return nights
    }

    /** First instant (to the second) in (a, b] whose darkness differs from a's. */
    private fun edge(a: Instant, b: Instant, dark: (Instant) -> Boolean): Instant {
        val aDark = dark(a)
        var lo = a
        var hi = b
        while (Duration.between(lo, hi).seconds > 1) {
            val mid = lo.plus(Duration.between(lo, hi).dividedBy(2))
            if (dark(mid) == aDark) lo = mid else hi = mid
        }
        return hi
    }

    /** Sun's elevation above the horizon in degrees, ignoring refraction. */
    private fun elevationDeg(t: Instant, lat: Double, lon: Double): Double {
        val julianDay = t.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val c = (julianDay - 2_451_545.0) / 36_525.0 // Julian centuries since J2000
        val meanLong = (280.46646 + c * (36_000.76983 + c * 0.0003032)).mod(360.0)
        val meanAnomaly = 357.52911 + c * (35_999.05029 - 0.0001537 * c)
        val eccentricity = 0.016708634 - c * (0.000042037 + 0.0000001267 * c)
        val m = rad(meanAnomaly)
        val center = sin(m) * (1.914602 - c * (0.004817 + 0.000014 * c)) +
            sin(2 * m) * (0.019993 - 0.000101 * c) + sin(3 * m) * 0.000289
        val omega = rad(125.04 - 1_934.136 * c)
        val apparentLong = rad(meanLong + center - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity = 23.0 + (26.0 + (21.448 - c * (46.815 + c * (0.00059 - c * 0.001813))) / 60.0) / 60.0
        val obliquity = rad(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparentLong))

        val y = tan(obliquity / 2).let { it * it }
        val l0 = rad(meanLong)
        val equationOfTimeMin = 4 * Math.toDegrees(
            y * sin(2 * l0) - 2 * eccentricity * sin(m) + 4 * eccentricity * y * sin(m) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m),
        )
        val utcMinutes = (t.epochSecond.mod(86_400L)) / 60.0
        val trueSolarMin = (utcMinutes + equationOfTimeMin + 4 * lon).mod(1_440.0)
        val hourAngle = rad(trueSolarMin / 4 - 180)
        val phi = rad(lat)
        val cosZenith = sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle)
        return 90 - Math.toDegrees(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    private fun rad(deg: Double) = Math.toRadians(deg)
}
