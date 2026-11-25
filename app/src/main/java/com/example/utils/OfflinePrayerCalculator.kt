package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.example.model.PrayerLocationSettings
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

data class PrayerTimeSlot(
    val id: String,
    val titleAr: String,
    val hour: Int,
    val minute: Int,
    val notifyEnabled: Boolean
) {
    fun formattedArabic(): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val period = if (hour >= 12) "م" else "ص"
        return "%d:%02d %s".format(h12, minute, period)
    }

    fun formatted24(): String = "%02d:%02d".format(hour, minute)
}

data class DailyPrayerSchedule(
    val cityName: String,
    val prayers: List<PrayerTimeSlot>,
    val nextPrayerTitle: String,
    val nextPrayerRemainingText: String
)

data class IraqiCityPreset(
    val nameAr: String,
    val latitude: Double,
    val longitude: Double
)

object OfflinePrayerCalculator {

    val iraqiCities: List<IraqiCityPreset> = listOf(
        IraqiCityPreset("الفلوجة - الأنبار", 33.3491, 43.7859),
        IraqiCityPreset("الرمادي - الأنبار", 33.4271, 43.2992),
        IraqiCityPreset("بغداد", 33.3152, 44.3661),
        IraqiCityPreset("البصرة", 30.5085, 47.7804),
        IraqiCityPreset("نينوى / الموصل", 36.3489, 43.1577),
        IraqiCityPreset("أربيل", 36.1911, 44.0092),
        IraqiCityPreset("النجف الأشرف", 31.9996, 44.3148),
        IraqiCityPreset("كربلاء المقدسة", 32.6160, 44.0249),
        IraqiCityPreset("كركوك", 35.4681, 44.3922),
        IraqiCityPreset("ذي قار / الناصرية", 31.0579, 46.2573),
        IraqiCityPreset("بابل / الحلة", 32.4724, 44.4196),
        IraqiCityPreset("صلاح الدين / تكريت", 34.6158, 43.6786),
        IraqiCityPreset("ديالى / بعقوبة", 33.7484, 44.6436),
        IraqiCityPreset("واسط / الكوت", 32.5128, 45.8182),
        IraqiCityPreset("ميسان / العمارة", 31.8356, 47.1448),
        IraqiCityPreset("المثنى / السماوة", 31.3186, 45.2806),
        IraqiCityPreset("القادسية / الديوانية", 31.9929, 44.9255),
        IraqiCityPreset("السليمانية", 35.5650, 45.4377),
        IraqiCityPreset("دهوك", 36.8669, 42.9503),
        IraqiCityPreset("مكة المكرمة", 21.3891, 39.8579)
    )

    private fun fixAngle(a: Double): Double {
        var r = a - 360.0 * floor(a / 360.0)
        if (r < 0) r += 360.0
        return r
    }

    private fun fixHour(a: Double): Double {
        var r = a - 24.0 * floor(a / 24.0)
        if (r < 0) r += 24.0
        return r
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = Math.toDegrees(atan2(cos(Math.toRadians(e)) * sin(Math.toRadians(l)), cos(Math.toRadians(l)))) / 15.0
        val decl = Math.toDegrees(asin(sin(Math.toRadians(e)) * sin(Math.toRadians(l))))
        val eqt = q / 15.0 - fixHour(ra)
        return decl to eqt
    }

    private fun sunAngleTime(jd: Double, lat: Double, angle: Double, isMorning: Boolean): Double {
        val (decl, eqt) = sunPosition(jd)
        val noon = fixHour(12.0 - eqt)
        val latRad = Math.toRadians(lat)
        val declRad = Math.toRadians(decl)
        val num = -sin(Math.toRadians(angle)) - sin(latRad) * sin(declRad)
        val den = cos(latRad) * cos(declRad)
        val ratio = (num / den).coerceIn(-0.999, 0.999)
        val t = Math.toDegrees(acos(ratio)) / 15.0
        return noon + if (isMorning) -t else t
    }

    private fun asrTime(jd: Double, lat: Double, factor: Double = 1.0): Double {
        val (decl, eqt) = sunPosition(jd)
        val noon = fixHour(12.0 - eqt)
        val angle = -Math.toDegrees(atan(1.0 / (factor + tan(Math.toRadians(abs(lat - decl))))))
        val latRad = Math.toRadians(lat)
        val declRad = Math.toRadians(decl)
        val num = -sin(Math.toRadians(angle)) - sin(latRad) * sin(declRad)
        val den = cos(latRad) * cos(declRad)
        val ratio = (num / den).coerceIn(-0.999, 0.999)
        val t = Math.toDegrees(acos(ratio)) / 15.0
        return noon + t
    }

    private fun toHourMinute(hours: Double): Pair<Int, Int> {
        val fixed = fixHour(hours + 0.5 / 60.0)
        val h = floor(fixed).toInt().coerceIn(0, 23)
        val m = floor((fixed - h) * 60.0).toInt().coerceIn(0, 59)
        return h to m
    }

    fun computeSchedule(
        settings: PrayerLocationSettings,
        cal: Calendar = Calendar.getInstance()
    ): DailyPrayerSchedule {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val jd = julianDate(year, month, day) - settings.longitude / (15.0 * 24.0)
        val tzOffset = settings.timezoneOffsetHours - settings.longitude / 15.0

        val (_, eqt) = sunPosition(jd + 0.5)
        val dhuhrHour = fixHour(12.0 - eqt + tzOffset)
        val fajrHour = sunAngleTime(jd + 5.0 / 24.0, settings.latitude, 18.5, true) + tzOffset
        val sunriseHour = sunAngleTime(jd + 6.0 / 24.0, settings.latitude, 0.833, true) + tzOffset
        val asrHour = asrTime(jd + 15.0 / 24.0, settings.latitude, 1.0) + tzOffset
        val maghribHour = sunAngleTime(jd + 18.0 / 24.0, settings.latitude, 0.833, false) + tzOffset
        val ishaHour = sunAngleTime(jd + 19.5 / 24.0, settings.latitude, 17.5, false) + tzOffset

        val (fH, fM) = toHourMinute(fajrHour)
        val (sH, sM) = toHourMinute(sunriseHour)
        val (dH, dM) = toHourMinute(dhuhrHour)
        val (aH, aM) = toHourMinute(asrHour)
        val (mH, mM) = toHourMinute(maghribHour)
        val (iH, iM) = toHourMinute(ishaHour)

        val list = listOf(
            PrayerTimeSlot("fajr", "الفجر", fH, fM, settings.fajrNotifyEnabled),
            PrayerTimeSlot("sunrise", "الشروق", sH, sM, false),
            PrayerTimeSlot("dhuhr", "الظهر", dH, dM, settings.dhuhrNotifyEnabled),
            PrayerTimeSlot("asr", "العصر", aH, aM, settings.asrNotifyEnabled),
            PrayerTimeSlot("maghrib", "المغرب", mH, mM, settings.maghribNotifyEnabled),
            PrayerTimeSlot("isha", "العشاء", iH, iM, settings.ishaNotifyEnabled)
        )

        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val obligatory = list.filter { it.id != "sunrise" }
        val next = obligatory.firstOrNull { (it.hour * 60 + it.minute) > nowMin }
        val (nextTitle, diffMin) = if (next != null) {
            next.titleAr to ((next.hour * 60 + next.minute) - nowMin)
        } else {
            val fajr = obligatory.first()
            fajr.titleAr to ((24 * 60 - nowMin) + (fajr.hour * 60 + fajr.minute))
        }
        val hrs = diffMin / 60
        val mins = diffMin % 60
        val remText = if (hrs > 0) "متبقي $hrs ساعة و $mins دقيقة" else "متبقي $mins دقيقة"

        return DailyPrayerSchedule(
            cityName = settings.cityName,
            prayers = list,
            nextPrayerTitle = nextTitle,
            nextPrayerRemainingText = remText
        )
    }

    fun nearestCityName(lat: Double, lon: Double): String {
        val nearest = iraqiCities.minByOrNull { c ->
            val dLat = c.latitude - lat
            val dLon = c.longitude - lon
            dLat * dLat + dLon * dLon
        }
        return nearest?.nameAr ?: "الموقع الحالي"
    }

    @SuppressLint("MissingPermission")
    fun readDeviceLocationSync(context: Context): PrayerLocationSettings? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
                if (best == null || loc.accuracy < best.accuracy) {
                    best = loc
                }
            }
            val location = best ?: return null
            val lat = location.latitude
            val lon = location.longitude
            val tzHours = java.util.TimeZone.getDefault().rawOffset / 3600000.0
            val geocodedName = runCatching {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale("ar")).getFromLocation(lat, lon, 1)
                val addr = addresses?.firstOrNull()
                listOfNotNull(addr?.locality, addr?.adminArea).joinToString(" - ")
            }.getOrNull().orEmpty().ifBlank { nearestCityName(lat, lon) }

            PrayerLocationSettings(
                cityName = geocodedName,
                latitude = lat,
                longitude = lon,
                timezoneOffsetHours = tzHours
            )
        } catch (_: Exception) {
            null
        }
    }
}
