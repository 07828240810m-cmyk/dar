package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val CairoFontFamily = FontFamily(
    Font(R.font.cairo, FontWeight.Normal),
    Font(R.font.cairo, FontWeight.Medium),
    Font(R.font.cairo, FontWeight.SemiBold),
    Font(R.font.cairo, FontWeight.Bold)
)

val TajawalFontFamily = FontFamily(
    Font(R.font.tajawal, FontWeight.Normal),
    Font(R.font.tajawal, FontWeight.Medium),
    Font(R.font.tajawal, FontWeight.Bold)
)

val AlmaraiFontFamily = FontFamily(
    Font(R.font.almarai, FontWeight.Normal),
    Font(R.font.almarai, FontWeight.Bold)
)

val AmiriFontFamily = FontFamily(
    Font(R.font.amiri, FontWeight.Normal),
    Font(R.font.amiri, FontWeight.Bold)
)

val ReadexProFontFamily = FontFamily(
    Font(R.font.readex_pro, FontWeight.Normal),
    Font(R.font.readex_pro, FontWeight.Medium),
    Font(R.font.readex_pro, FontWeight.Bold)
)

val IbmPlexSansArabicFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic, FontWeight.Bold)
)

val ScheherazadeNewFontFamily = FontFamily(
    Font(R.font.scheherazade_new, FontWeight.Normal),
    Font(R.font.scheherazade_new, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.Bold)
)

val LoraFontFamily = FontFamily(
    Font(R.font.lora, FontWeight.Normal),
    Font(R.font.lora, FontWeight.Bold)
)

enum class AppFontOption(val id: String, val titleAr: String, val family: FontFamily) {
    CAIRO("cairo", "القاهرة (Cairo) - واضح وعصري", CairoFontFamily),
    TAJAWAL("tajawal", "تجوال (Tajawal) - خفيف ومريح", TajawalFontFamily),
    ALMARAI("almarai", "المراعي (Almarai) - بارز وواضح للامتحانات", AlmaraiFontFamily),
    AMIRI("amiri", "أميري (Amiri) - نسخي كلاسيكي", AmiriFontFamily),
    READEX_PRO("readex", "ريديكس (Readex Pro) - هندسي متوازن", ReadexProFontFamily),
    IBM_PLEX_SANS_ARABIC("ibm_plex_arabic", "IBM Plex Sans Arabic - أكاديمي رصين", IbmPlexSansArabicFontFamily),
    SCHEHERAZADE_NEW("scheherazade", "شهرزاد (Scheherazade New) - تراثي قرآني", ScheherazadeNewFontFamily),
    INTER("inter", "Inter - لاتيني/علمي واضح", InterFontFamily),
    LORA("lora", "Lora - لاتيني/أدبي", LoraFontFamily)
}

fun buildAppTypography(fontOption: AppFontOption): Typography {
    val family = fontOption.family
    return Typography(
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 30.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 26.sp
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 10.5.sp,
            lineHeight = 14.sp
        )
    )
}
