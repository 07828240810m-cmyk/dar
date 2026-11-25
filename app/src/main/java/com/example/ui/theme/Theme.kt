package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.SubjectSpecialty

enum class SeasonalThemePreset(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val emoji: String,
    val primaryOverride: Color?,
    val secondaryOverride: Color?,
    val waveStartOverride: Color?,
    val waveEndOverride: Color?,
    val waveAccentOverride: Color?
) {
    NONE("NONE", "الافتراضي (بدون موسم)", "مظهر المدرس المحفوظ", "", null, null, null, null, null),
    RAMADAN(
        "RAMADAN",
        "رمضان المبارك",
        "مظهر رمضاني روحاني مؤقت",
        "🌙",
        Color(0xFFF59E0B),
        Color(0xFF10B981),
        Color(0xFF062C22),
        Color(0xFF0E4D3B),
        Color(0xFFFBBF24)
    ),
    EID(
        "EID",
        "أيام العيد السعيد",
        "احتفالية العيد المبارك",
        "✨",
        Color(0xFFEC4899),
        Color(0xFFFACC15),
        Color(0xFF3B0764),
        Color(0xFF6B21A8),
        Color(0xFFFDE047)
    ),
    TEACHER_DAY(
        "TEACHER_DAY",
        "عيد المعلم",
        "تحية تقدير وامتنان لكل معلم",
        "🎓",
        Color(0xFF0EA5E9),
        Color(0xFFF59E0B),
        Color(0xFF0C2D57),
        Color(0xFF154E93),
        Color(0xFFFCD34D)
    ),
    EXAMS(
        "EXAMS",
        "موسم الامتحانات",
        "مظهر تركيز هادئ للامتحانات",
        "📝",
        Color(0xFF38BDF8),
        Color(0xFF34D399),
        Color(0xFF0B1F36),
        Color(0xFF13385E),
        Color(0xFF38BDF8)
    ),
    AUTUMN(
        "AUTUMN",
        "فصل الخريف الدافئ",
        "ألوان خريفية هادئة ومريحة للعين",
        "🍂",
        Color(0xFFEA580C),
        Color(0xFFFBBF24),
        Color(0xFF3E1E0B),
        Color(0xFF693312),
        Color(0xFFF59E0B)
    ),
    DARK_GOLD(
        "DARK_GOLD",
        "الذهبي الفاخر",
        "ثيم احتفالي فاخر باللون الذهبي",
        "👑",
        Color(0xFFFFD54F),
        Color(0xFF22D3EE),
        Color(0xFF1C1709),
        Color(0xFF362A0D),
        Color(0xFFFFD54F)
    );

    companion object {
        fun fromId(id: String?): SeasonalThemePreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

data class ExtendedColors(
    val cardElevated: Color,
    val cardSecondary: Color = cardElevated,
    val borderSubtle: Color,
    val textMuted: Color,
    val accentHighlight: Color,
    val neonGlowEnabled: Boolean = true,
    val bottomNavBg: Color = Color(0xFF081B36),
    val bottomNavActivePill: Color = Color(0xFFFFC83D),
    val bottomNavActiveContent: Color = Color(0xFF061124),
    val bottomNavInactiveContent: Color = Color(0xFFE2E8F0),
    val headerWaveStart: Color = Color(0xFF0A244A),
    val headerWaveEnd: Color = Color(0xFF103466),
    val headerWaveAccent: Color = Color(0xFFFFC83D),
    val paletteId: String = ThemePalette.DESIGN_7_MIDNIGHT_NEON.id,
    val isDark: Boolean = true
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        cardElevated = Color(0xFF0D2850),
        cardSecondary = Color(0xFF091D3D),
        borderSubtle = Color(0xFF22D3EE).copy(alpha = 0.35f),
        textMuted = Color(0xFFB8D0F0),
        accentHighlight = Color(0xFFFFC83D)
    )
}

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TeacherLedgerTheme(
    palette: ThemePalette = ThemePalette.DESIGN_7_MIDNIGHT_NEON,
    specialty: SubjectSpecialty = SubjectSpecialty.BIOLOGY,
    useSpecialtyTheme: Boolean = false,
    textTone: AppTextTone = AppTextTone.AUTO,
    darkTheme: Boolean = true,
    neonGlowEnabled: Boolean = true,
    fontOption: AppFontOption = AppFontOption.CAIRO,
    fontScaleMultiplier: Float = 1.0f,
    seasonalThemeId: String = "NONE",
    customColorsEnabled: Boolean = false,
    customTextColorHex: Long = 0L,
    customButtonColorHex: Long = 0L,
    customBgColorHex: Long = 0L,
    customCardAccentColorHex: Long = 0L,
    content: @Composable () -> Unit
) {
    val seasonalPreset = remember(seasonalThemeId) { SeasonalThemePreset.fromId(seasonalThemeId) }
    val activeColors = if (useSpecialtyTheme) {
        specialty.paletteSpec()
    } else {
        SubjectColorSpec(
            darkBg = palette.darkBg,
            darkSurface = palette.darkSurface,
            darkCard = palette.darkCard,
            darkPrimary = palette.darkPrimary,
            darkSecondary = palette.darkSecondary,
            lightBg = palette.lightBg,
            lightSurface = palette.lightSurface,
            lightPrimary = palette.lightPrimary,
            lightSecondary = palette.lightSecondary
        )
    }

    val hasAnyWheelOverride = customColorsEnabled ||
        customTextColorHex != 0L ||
        customButtonColorHex != 0L ||
        customBgColorHex != 0L ||
        customCardAccentColorHex != 0L

    val wheelBg = if (hasAnyWheelOverride && customBgColorHex != 0L) Color(customBgColorHex) else null
    val wheelText = if (hasAnyWheelOverride && customTextColorHex != 0L) Color(customTextColorHex) else null
    val wheelButton = if (hasAnyWheelOverride && customButtonColorHex != 0L) Color(customButtonColorHex) else null
    val wheelCardAccent = if (hasAnyWheelOverride && customCardAccentColorHex != 0L) Color(customCardAccentColorHex) else null

    val effectiveDark = wheelBg?.let { it.luminance() < 0.45f } ?: darkTheme
    val isBlackMint = !useSpecialtyTheme && palette == ThemePalette.BLACK_NEON_MINT && effectiveDark && wheelBg == null

    val darkPrimaryColor = when (textTone) {
        AppTextTone.AUTO -> activeColors.darkPrimary
        AppTextTone.MINT_02C39A -> Color(0xFF02C39A)
        AppTextTone.YELLOW -> Color(0xFFFFD54F)
        AppTextTone.TURQUOISE -> Color(0xFF40D6B4)
        AppTextTone.WHITE -> Color(0xFFFFFFFF)
    }
    val darkSecondaryColor = when (textTone) {
        AppTextTone.AUTO -> activeColors.darkSecondary
        AppTextTone.MINT_02C39A -> Color(0xFF02C39A)
        AppTextTone.YELLOW -> Color(0xFF40D6B4)
        AppTextTone.TURQUOISE -> Color(0xFFFFD54F)
        AppTextTone.WHITE -> Color(0xFF40D6B4)
    }
    val darkOnSurfaceText = when (textTone) {
        AppTextTone.AUTO -> if (isBlackMint) Color(0xFF02C39A) else CrispWhite
        AppTextTone.MINT_02C39A -> Color(0xFF02C39A)
        AppTextTone.YELLOW -> Color(0xFFFFEB7A)
        AppTextTone.TURQUOISE -> Color(0xFFE0FFF8)
        AppTextTone.WHITE -> CrispWhite
    }
    val darkMutedText = when {
        wheelText != null -> wheelText.copy(alpha = 0.76f)
        else -> when (textTone) {
            AppTextTone.AUTO -> when {
                isBlackMint -> Color(0xFF02C39A).copy(alpha = 0.82f)
                palette == ThemePalette.DESIGN_7_MIDNIGHT_NEON -> Color(0xFFBFD7F7)
                else -> Color(0xFFCBD5E1)
            }
            AppTextTone.MINT_02C39A -> Color(0xFF02C39A).copy(alpha = 0.82f)
            AppTextTone.YELLOW -> Color(0xFFE6C86E)
            AppTextTone.TURQUOISE -> Color(0xFF99F6E4)
            AppTextTone.WHITE -> Color(0xFFE2E8F0)
        }
    }

    val useImageLightPalette = !effectiveDark && !useSpecialtyTheme && palette.isImageDesignPreset
    val useScreenshotLightAccents = !effectiveDark && !useSpecialtyTheme && palette.isScreenshotStyle && textTone == AppTextTone.AUTO

    val targetBg = wheelBg ?: when {
        effectiveDark -> activeColors.darkBg
        useImageLightPalette -> activeColors.lightBg
        else -> Color(0xFFFFFFFF)
    }
    val targetSurface = when {
        wheelBg != null -> if (effectiveDark) lerp(wheelBg, Color.White, 0.06f) else lerp(wheelBg, Color.White, 0.45f)
        effectiveDark -> activeColors.darkSurface
        else -> Color(0xFFFFFFFF)
    }
    val targetCard = when {
        wheelBg != null && wheelCardAccent != null -> {
            val lifted = if (effectiveDark) lerp(wheelBg, Color.White, 0.11f) else lerp(wheelBg, Color.White, 0.7f)
            lerp(lifted, wheelCardAccent, if (effectiveDark) 0.14f else 0.08f)
        }
        wheelBg != null -> if (effectiveDark) lerp(wheelBg, Color.White, 0.11f) else lerp(wheelBg, Color.White, 0.65f)
        wheelCardAccent != null -> {
            val base = if (effectiveDark) activeColors.darkCard else Color(0xFFFFFFFF)
            lerp(base, wheelCardAccent, if (effectiveDark) 0.16f else 0.09f)
        }
        effectiveDark -> activeColors.darkCard
        else -> Color(0xFFFFFFFF)
    }
    val targetPrimary = wheelButton ?: when {
        effectiveDark -> darkPrimaryColor
        useImageLightPalette || useScreenshotLightAccents -> activeColors.lightPrimary
        else -> Color(0xFF000000)
    }
    val targetSecondary = wheelCardAccent ?: when {
        effectiveDark -> darkSecondaryColor
        useImageLightPalette || useScreenshotLightAccents -> activeColors.lightSecondary
        else -> Color(0xFF111827)
    }
    val targetOnBg = wheelText ?: if (effectiveDark) darkOnSurfaceText else Color(0xFF0B1324)

    val animBg by androidx.compose.animation.animateColorAsState(
        targetValue = targetBg,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "theme_bg"
    )
    val animSurface by androidx.compose.animation.animateColorAsState(
        targetValue = targetSurface,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "theme_surface"
    )
    val animCard by androidx.compose.animation.animateColorAsState(
        targetValue = targetCard,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "theme_card"
    )
    val animPrimary by androidx.compose.animation.animateColorAsState(
        targetValue = if (wheelButton != null) wheelButton else (seasonalPreset.primaryOverride ?: targetPrimary),
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "theme_primary"
    )
    val animSecondary by androidx.compose.animation.animateColorAsState(
        targetValue = if (wheelCardAccent != null) wheelCardAccent else (seasonalPreset.secondaryOverride ?: targetSecondary),
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "theme_secondary"
    )
    val animOnBg by androidx.compose.animation.animateColorAsState(
        targetValue = targetOnBg,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "theme_on_bg"
    )

    val resolvedOnPrimary = if (animPrimary.luminance() > 0.52f) Color(0xFF061124) else CrispWhite
    val resolvedOnSecondary = if (animSecondary.luminance() > 0.52f) Color(0xFF061124) else CrispWhite

    val colorScheme = if (effectiveDark) {
        darkColorScheme(
            primary = animPrimary,
            onPrimary = resolvedOnPrimary,
            primaryContainer = animCard,
            onPrimaryContainer = animPrimary,
            secondary = animSecondary,
            onSecondary = resolvedOnSecondary,
            secondaryContainer = animSurface,
            onSecondaryContainer = animOnBg,
            tertiary = animSecondary,
            onTertiary = resolvedOnSecondary,
            background = animBg,
            onBackground = animOnBg,
            surface = animSurface,
            onSurface = animOnBg,
            surfaceVariant = animCard,
            onSurfaceVariant = darkMutedText,
            outline = animPrimary.copy(alpha = 0.50f),
            outlineVariant = Color.White.copy(alpha = 0.16f),
            error = DangerRed,
            onError = CrispWhite
        )
    } else {
        lightColorScheme(
            primary = animPrimary,
            onPrimary = resolvedOnPrimary,
            primaryContainer = if (wheelButton != null || useImageLightPalette || useScreenshotLightAccents) animPrimary.copy(alpha = 0.14f) else Color(0xFFF3F4F6),
            onPrimaryContainer = if (wheelButton != null || useImageLightPalette || useScreenshotLightAccents) animPrimary else Color(0xFF000000),
            secondary = animSecondary,
            onSecondary = resolvedOnSecondary,
            secondaryContainer = if (wheelCardAccent != null || useImageLightPalette || useScreenshotLightAccents) animSecondary.copy(alpha = 0.15f) else Color(0xFFE5E7EB),
            onSecondaryContainer = if (wheelText != null) animOnBg else Color(0xFF000000),
            tertiary = animSecondary,
            onTertiary = resolvedOnSecondary,
            background = animBg,
            onBackground = animOnBg,
            surface = animSurface,
            onSurface = animOnBg,
            surfaceVariant = if (wheelCardAccent != null) lerp(Color(0xFFF1F5F9), wheelCardAccent, 0.12f) else if (palette == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL) Color(0xFFF1ECFF) else Color(0xFFF1F5F9),
            onSurfaceVariant = wheelText?.copy(alpha = 0.78f) ?: Color(0xFF1E293B),
            outline = animPrimary,
            outlineVariant = Color(0xFFD1D5DB),
            error = Color(0xFFDC2626),
            onError = CrispWhite
        )
    }

    val navBg = when {
        wheelBg != null -> if (effectiveDark) lerp(wheelBg, Color.Black, 0.25f) else lerp(wheelBg, Color(0xFF0B1F3F), 0.75f)
        !useSpecialtyTheme && palette == ThemePalette.BLACK_NEON_MINT && effectiveDark -> Color(0xFF050A09)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL -> Color(0xFF53249E)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_5_OCEAN_CRYSTAL -> Color(0xFF082A61)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_6_PURE_SKY_BLUE -> Color(0xFF0C2348)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_7_MIDNIGHT_NEON -> Color(0xFF081B36)
        effectiveDark -> activeColors.darkSurface
        else -> Color(0xFF0B1F3F)
    }
    val navActivePill = when {
        wheelButton != null -> animPrimary
        wheelCardAccent != null -> animSecondary
        !useSpecialtyTheme && palette == ThemePalette.BLACK_NEON_MINT && effectiveDark -> Color(0xFF02C39A)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_6_PURE_SKY_BLUE -> Color(0xFF2998FF)
        !useSpecialtyTheme && (palette == ThemePalette.DESIGN_7_MIDNIGHT_NEON ||
            palette == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL ||
            palette == ThemePalette.DESIGN_5_OCEAN_CRYSTAL) -> Color(0xFFFFC83D)
        effectiveDark -> animPrimary
        else -> Color(0xFFFFC83D)
    }
    val navActiveContent = when {
        wheelButton != null || wheelCardAccent != null -> if (navActivePill.luminance() > 0.52f) Color(0xFF081224) else CrispWhite
        palette == ThemePalette.DESIGN_6_PURE_SKY_BLUE -> Color(0xFFFFFFFF)
        isBlackMint -> Color(0xFF000000)
        else -> Color(0xFF081224)
    }

    val waveStart = when {
        wheelBg != null -> if (effectiveDark) lerp(wheelBg, Color.Black, 0.20f) else lerp(animPrimary, Color(0xFF0B1F3F), 0.40f)
        seasonalPreset.waveStartOverride != null -> seasonalPreset.waveStartOverride
        !useSpecialtyTheme && palette == ThemePalette.BLACK_NEON_MINT && effectiveDark -> Color(0xFF000000)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL -> if (effectiveDark) Color(0xFF23114F) else Color(0xFF5B21B6)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_5_OCEAN_CRYSTAL -> if (effectiveDark) Color(0xFF08234E) else Color(0xFF0A3D86)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_6_PURE_SKY_BLUE -> if (effectiveDark) Color(0xFF0F264A) else Color(0xFF1E64D8)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_7_MIDNIGHT_NEON -> Color(0xFF071B38)
        else -> animSurface
    }
    val waveEnd = when {
        wheelBg != null -> lerp(waveStart, animSecondary, 0.28f)
        seasonalPreset.waveEndOverride != null -> seasonalPreset.waveEndOverride
        !useSpecialtyTheme && palette == ThemePalette.BLACK_NEON_MINT && effectiveDark -> Color(0xFF0B1B16)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL -> if (effectiveDark) Color(0xFF391C7A) else Color(0xFF7C3AED)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_5_OCEAN_CRYSTAL -> if (effectiveDark) Color(0xFF103978) else Color(0xFF2563EB)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_6_PURE_SKY_BLUE -> if (effectiveDark) Color(0xFF163A70) else Color(0xFF38BDF8)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_7_MIDNIGHT_NEON -> Color(0xFF0E2F60)
        else -> animCard
    }
    val waveAccent = when {
        wheelCardAccent != null -> animSecondary
        wheelButton != null -> animPrimary
        seasonalPreset.waveAccentOverride != null -> seasonalPreset.waveAccentOverride
        isBlackMint -> Color(0xFF02C39A)
        !useSpecialtyTheme && palette == ThemePalette.DESIGN_6_PURE_SKY_BLUE -> Color(0xFF38BDF8)
        else -> Color(0xFFFFC83D)
    }

    val extended = if (effectiveDark) {
        ExtendedColors(
            cardElevated = animCard,
            cardSecondary = animSurface,
            borderSubtle = animSecondary.copy(alpha = if (neonGlowEnabled) 0.55f else 0.28f),
            textMuted = darkMutedText,
            accentHighlight = animPrimary,
            neonGlowEnabled = neonGlowEnabled,
            bottomNavBg = navBg,
            bottomNavActivePill = navActivePill,
            bottomNavActiveContent = navActiveContent,
            bottomNavInactiveContent = wheelText?.copy(alpha = 0.85f) ?: Color(0xFFE6EEF9),
            headerWaveStart = waveStart,
            headerWaveEnd = waveEnd,
            headerWaveAccent = waveAccent,
            paletteId = palette.id,
            isDark = true
        )
    } else {
        ExtendedColors(
            cardElevated = if (wheelCardAccent != null) lerp(Color.White, wheelCardAccent, 0.08f) else Color(0xFFFFFFFF),
            cardSecondary = if (wheelCardAccent != null) lerp(Color(0xFFF3F7FF), wheelCardAccent, 0.12f) else if (palette == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL) Color(0xFFF3EEFF) else Color(0xFFF3F7FF),
            borderSubtle = if (wheelCardAccent != null || useImageLightPalette || useScreenshotLightAccents) animSecondary.copy(alpha = if (neonGlowEnabled) 0.36f else 0.22f) else Color(0xFF000000).copy(alpha = 0.18f),
            textMuted = wheelText?.copy(alpha = 0.74f) ?: Color(0xFF334155),
            accentHighlight = animPrimary,
            neonGlowEnabled = neonGlowEnabled,
            bottomNavBg = navBg,
            bottomNavActivePill = navActivePill,
            bottomNavActiveContent = navActiveContent,
            bottomNavInactiveContent = Color(0xFFF8FAFC),
            headerWaveStart = waveStart,
            headerWaveEnd = waveEnd,
            headerWaveAccent = waveAccent,
            paletteId = palette.id,
            isDark = false
        )
    }

    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity, fontScaleMultiplier) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScaleMultiplier.coerceIn(0.85f, 1.5f)
        )
    }
    val typography = remember(fontOption) { buildAppTypography(fontOption) }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalExtendedColors provides extended,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
