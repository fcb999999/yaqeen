package app.yaqeen.feature.prayer.times

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** حسابات القبلة الخالصة: الاتجاه الأولي على الدائرة الكبرى نحو الكعبة، وتنعيم قراءات البوصلة. */
object QiblaMath {
    /** إحداثيات الكعبة المشرفة. */
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    /** زاوية القبلة من الشمال الحقيقي باتجاه عقارب الساعة (0..360). */
    fun bearingDegrees(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - lon)
        val y = sin(dl) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dl)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    fun distanceKm(lat: Double, lon: Double): Double = CityRepository.distanceKm(lat, lon, KAABA_LAT, KAABA_LON)

    /** يحصر الزاوية في [0, 360). */
    fun normalize(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** أصغر فرق بين زاويتين مع الإشارة، في (-180, 180]. موجب = [to] أبعد باتجاه عقارب الساعة. */
    fun signedDelta(from: Double, to: Double): Double {
        var d = normalize(to - from)
        if (d > 180.0) d -= 360.0
        return d
    }

    fun absDelta(a: Double, b: Double): Double = abs(signedDelta(a, b))

    /** اسم الجهة التقريبي بالعربية للزاوية (للعرض النصي). */
    fun directionName(deg: Double): String {
        val names = listOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")
        return names[(((normalize(deg) + 22.5) / 45.0).toInt()) % 8]
    }
}

/**
 * مرشح تمرير منخفض (low-pass) للزوايا الدائرية: يمزج الزاوية الجديدة مع السابقة بمعامل [alpha]
 * عبر أقصر فرق دائري، فلا يقفز عند العبور بين 359° و0°.
 */
class CircularLowPass(private val alpha: Double = 0.15) {
    private var current: Double? = null

    fun reset() { current = null }

    fun update(angle: Double): Double {
        val prev = current
        val next = if (prev == null) QiblaMath.normalize(angle) else QiblaMath.normalize(prev + alpha * QiblaMath.signedDelta(prev, angle))
        current = next
        return next
    }
}
