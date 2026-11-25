package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Base Colors
val PureBlack = Color(0xFF000000)
val NavyDeep = Color(0xFF0A1229)
val NavySurface = Color(0xFF111D3D)
val NavyCard = Color(0xFF192A56)
val NavyBorder = Color(0xFF2B417A)
val TurquoiseAccent = Color(0xFF40D6B4)
val TurquoiseBright = Color(0xFF64E8CB)
val TurquoiseDeep = Color(0xFF1FA688)
val GoldBright = Color(0xFFFFD54F)
val GoldDeep = Color(0xFFE0A800)
val CrispWhite = Color(0xFFFFFFFF)
val SoftWhite = Color(0xFFF1F5F9)
val MutedSlate = Color(0xFFB6C6E3)

// Semantic Status Colors
val SuccessGreen = Color(0xFF40D6B4)
val SuccessGreenBg = Color(0xFF0E332C)
val WarningAmber = Color(0xFFFFD54F)
val WarningAmberBg = Color(0xFF3D2B10)
val DangerRed = Color(0xFFEF4444)
val DangerRedBg = Color(0xFF3F161D)
val InfoBlue = Color(0xFF38BDF8)
val InfoBlueBg = Color(0xFF103047)

// Kept only for legacy internal symbol color tokens if referenced
val MathTableAccent = Color(0xFFFF9F43)
val MathTableHeaderDark = Color(0xFF2D1B2E)
val MathTableSurfaceDark = Color(0xFF211524)
val MathTableRowAltDark = Color(0xFF2C1D30)
val MathTableHeaderLight = Color(0xFF4A2545)
val MathTableSurfaceLight = Color(0xFFFFF8F1)
val MathTableRowAltLight = Color(0xFFFFEDD5)

// Text Color Tone Selection (Yellow / Turquoise / White / Auto)
enum class AppTextTone(val id: String, val titleAr: String, val previewColor: Color) {
    AUTO("auto", "متناغم تلقائياً", Color(0xFF40D6B4)),
    MINT_02C39A("mint_02c39a", "أخضر نيون #02C39A", Color(0xFF02C39A)),
    YELLOW("yellow", "أصفر (الافتراضي)", Color(0xFFFFEB7A)),
    TURQUOISE("turquoise", "تركوازي", Color(0xFF40D6B4)),
    WHITE("white", "أبيض", Color(0xFFFFFFFF))
}

data class CustomColorPreset(val titleAr: String, val hex: Long)

val TimetableBgColorPresets = listOf(
    CustomColorPreset("لون التطبيق الافتراضي", 0L),
    CustomColorPreset("أبيض نقي (نهاري)", 0xFFFFFFFFL),
    CustomColorPreset("كحلي #102030", 0xFF102030L),
    CustomColorPreset("كحلي أعمق", 0xFF162B40L),
    CustomColorPreset("أخضر زمردي", 0xFF0C2A21L),
    CustomColorPreset("أزرق بحري", 0xFF0B2239L),
    CustomColorPreset("بنفسجي ملكي", 0xFF1D1338L),
    CustomColorPreset("عنابي داكن", 0xFF2B1222L),
    CustomColorPreset("بني كهرماني", 0xFF261D0CL),
    CustomColorPreset("أسود فاحم", 0xFF0A0D14L)
)

val TimetableAccentColorPresets = listOf(
    CustomColorPreset("لون التطبيق", 0L),
    CustomColorPreset("أسود ناصع (نهاري)", 0xFF000000L),
    CustomColorPreset("أصفر ذهبي", 0xFFFFD54FL),
    CustomColorPreset("تركوازي", 0xFF40D6B4L),
    CustomColorPreset("سماوي", 0xFF38BDF8L),
    CustomColorPreset("أخضر مشرق", 0xFF4ADE80L),
    CustomColorPreset("برتقالي", 0xFFFB923CL),
    CustomColorPreset("وردي", 0xFFF472B6L),
    CustomColorPreset("بنفسجي فاتح", 0xFFC084FCL),
    CustomColorPreset("أبيض", 0xFFFFFFFFL)
)

val StudentNameColorPresets = listOf(
    CustomColorPreset("لون النص الافتراضي", 0L),
    CustomColorPreset("أسود ناصع (نهاري)", 0xFF000000L),
    CustomColorPreset("أصفر ساطع", 0xFFFFEB7AL),
    CustomColorPreset("أصفر ذهبي", 0xFFFFD54FL),
    CustomColorPreset("أبيض ناصع", 0xFFFFFFFFL),
    CustomColorPreset("تركوازي", 0xFF40D6B4L),
    CustomColorPreset("سماوي", 0xFF38BDF8L),
    CustomColorPreset("أخضر نعناعي", 0xFF4ADE80L),
    CustomColorPreset("برتقالي دافئ", 0xFFFB923CL),
    CustomColorPreset("وردي فاتح", 0xFFF472B6L),
    CustomColorPreset("بنفسجي فاتح", 0xFFC084FCL)
)

val TileColorPresets = listOf(
    CustomColorPreset("افتراضي (أبيض في النهاري / كحلي في الليلي)", 0L),
    CustomColorPreset("أبيض نقي (PNG)", 0xFFFFFFFFL),
    CustomColorPreset("كحلي #102030", 0xFF102030L),
    CustomColorPreset("كحلي عميق", 0xFF1D3752L),
    CustomColorPreset("أسود فاخر", 0xFF0F172AL),
    CustomColorPreset("أصفر ذهبي", 0xFFFFD54FL),
    CustomColorPreset("تركوازي مشرق", 0xFF40D6B4L),
    CustomColorPreset("سماوي ناعم", 0xFF38BDF8L),
    CustomColorPreset("أخضر زمردي", 0xFF4ADE80L),
    CustomColorPreset("برتقالي دافئ", 0xFFFB923CL),
    CustomColorPreset("وردي أنيق", 0xFFF472B6L),
    CustomColorPreset("بنفسجي ملكي", 0xFFC084FCL)
)

// Student Row Color Tags
enum class StudentColorTag(val labelAr: String, val colorHex: Long) {
    NONE("بدون تلوين", 0x00000000),
    TURQUOISE("متميز (تركواز)", 0xFF40D6B4),
    GOLD("متفوق (ذهبي)", 0xFFFFD54F),
    GREEN("ممتاز (أخضر)", 0xFF22C55E),
    BLUE("متابعة (أزرق)", 0xFF38BDF8),
    AMBER("تنبيه (برتقالي)", 0xFFF59E0B),
    RED("إنذار (أحمر)", 0xFFEF4444)
}

// Complete Color Palette Choices selectable in Settings (Design 7 Default, Design 9 Second, plus 5 & 6 and 19 additional palettes)
enum class ThemePalette(
    val id: String,
    val titleAr: String,
    val darkBg: Color,
    val darkSurface: Color,
    val darkCard: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val lightBg: Color,
    val lightSurface: Color,
    val lightPrimary: Color,
    val lightSecondary: Color
) {
    DESIGN_7_MIDNIGHT_NEON(
        id = "design_7_midnight_neon",
        titleAr = "تصميم 7: الكحلي الليلي النيون (الافتراضي)",
        darkBg = Color(0xFF061124),
        darkSurface = Color(0xFF0A1D3B),
        darkCard = Color(0xFF0D2850),
        darkPrimary = Color(0xFFFFC83D),
        darkSecondary = Color(0xFF22D3EE),
        lightBg = Color(0xFFF3F8FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0B3A75),
        lightSecondary = Color(0xFF0EA5E9)
    ),
    DESIGN_9_ROYAL_PURPLE_PEARL(
        id = "design_9_royal_purple_pearl",
        titleAr = "تصميم 9: البنفسجي الملكي واللؤلؤي (الثاني)",
        darkBg = Color(0xFF150D30),
        darkSurface = Color(0xFF22144A),
        darkCard = Color(0xFF2D1B63),
        darkPrimary = Color(0xFFFFD24C),
        darkSecondary = Color(0xFFA78BFA),
        lightBg = Color(0xFFF7F5FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF5B21B6),
        lightSecondary = Color(0xFFF59E0B)
    ),
    DESIGN_5_OCEAN_CRYSTAL(
        id = "design_5_ocean_crystal",
        titleAr = "تصميم 5: الأزرق المحيطي المتألق والذهبي",
        darkBg = Color(0xFF071A36),
        darkSurface = Color(0xFF0D2852),
        darkCard = Color(0xFF143A73),
        darkPrimary = Color(0xFFFFC83D),
        darkSecondary = Color(0xFF38BDF8),
        lightBg = Color(0xFFF2F7FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0A3678),
        lightSecondary = Color(0xFF14B8A6)
    ),
    DESIGN_6_PURE_SKY_BLUE(
        id = "design_6_pure_sky_blue",
        titleAr = "تصميم 6: الأبيض السماوي النقي",
        darkBg = Color(0xFF0A192F),
        darkSurface = Color(0xFF112545),
        darkCard = Color(0xFF18345E),
        darkPrimary = Color(0xFF38BDF8),
        darkSecondary = Color(0xFF60A5FA),
        lightBg = Color(0xFFF8FAFC),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF1D6FE8),
        lightSecondary = Color(0xFF0EA5E9)
    ),
    BLACK_NEON_MINT(
        id = "black_neon_mint",
        titleAr = "الوضع الليلي البديل: أسود فاحم ونص #02C39A",
        darkBg = Color(0xFF000000),
        darkSurface = Color(0xFF070E0C),
        darkCard = Color(0xFF0E1A17),
        darkPrimary = Color(0xFF02C39A),
        darkSecondary = Color(0xFF02C39A),
        lightBg = Color(0xFFFFFFFF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF000000),
        lightSecondary = Color(0xFF02C39A)
    ),
    INDIGO_NAVY_TRIO(
        id = "indigo_navy_trio",
        titleAr = "1. كحلي #102030 ونص أصفر",
        darkBg = Color(0xFF102030),
        darkSurface = Color(0xFF162B40),
        darkCard = Color(0xFF1D3752),
        darkPrimary = Color(0xFFFFD54F),
        darkSecondary = Color(0xFF40D6B4),
        lightBg = Color(0xFFEEF2FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF102030),
        lightSecondary = Color(0xFF0F766E)
    ),
    NAVY_TURQUOISE_YELLOW(
        id = "navy_turquoise_yellow",
        titleAr = "2. كحلي عميق وتركوازي مشرق وأصفر",
        darkBg = Color(0xFF0C1B33),
        darkSurface = Color(0xFF132746),
        darkCard = Color(0xFF1A355E),
        darkPrimary = Color(0xFF40D6B4),
        darkSecondary = Color(0xFFFFD54F),
        lightBg = Color(0xFFF0F9FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0F766E),
        lightSecondary = Color(0xFFD97706)
    ),
    INDIGO_BRIGHT_GOLD(
        id = "indigo_bright_gold",
        titleAr = "3. نيلي ملكي وأصفر كهرماني",
        darkBg = Color(0xFF0F172A),
        darkSurface = Color(0xFF17223B),
        darkCard = Color(0xFF202F52),
        darkPrimary = Color(0xFFFACC15),
        darkSecondary = Color(0xFF38BDF8),
        lightBg = Color(0xFFF5F7FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF312E81),
        lightSecondary = Color(0xFFB45309)
    ),
    MIDNIGHT_CRISP_WHITE(
        id = "midnight_crisp_white",
        titleAr = "4. كحلي ليلي وأبيض ناصع",
        darkBg = Color(0xFF070F24),
        darkSurface = Color(0xFF0F1B3A),
        darkCard = Color(0xFF182852),
        darkPrimary = Color(0xFFFFFFFF),
        darkSecondary = Color(0xFF40D6B4),
        lightBg = Color(0xFFF8FAFC),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0F172A),
        lightSecondary = Color(0xFF0D9488)
    ),
    TURQUOISE_BLACK(
        id = "turquoise_black",
        titleAr = "5. أسود فاحم وتركوازي وذهبي",
        darkBg = Color(0xFF000000),
        darkSurface = Color(0xFF0B131E),
        darkCard = Color(0xFF142132),
        darkPrimary = Color(0xFF40D6B4),
        darkSecondary = Color(0xFFFFD54F),
        lightBg = Color(0xFFF4F8FA),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0B1F2E),
        lightSecondary = Color(0xFF149E82)
    ),
    BIOLOGY_EMERALD(
        id = "biology_emerald",
        titleAr = "6. أخضر زمردي داكن (الأحياء)",
        darkBg = Color(0xFF051A13),
        darkSurface = Color(0xFF09261D),
        darkCard = Color(0xFF0F3629),
        darkPrimary = Color(0xFF34D399),
        darkSecondary = Color(0xFFFFD54F),
        lightBg = Color(0xFFECFDF5),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF047857),
        lightSecondary = Color(0xFF10B981)
    ),
    ROYAL_VIOLET_GOLD(
        id = "royal_violet_gold",
        titleAr = "7. نيلي بنفسجي ملكي وأصفر",
        darkBg = Color(0xFF150E2E),
        darkSurface = Color(0xFF1F1642),
        darkCard = Color(0xFF2B1F5C),
        darkPrimary = Color(0xFFC084FC),
        darkSecondary = Color(0xFFFACC15),
        lightBg = Color(0xFFFAF5FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF6B21A8),
        lightSecondary = Color(0xFFD97706)
    ),
    OCEAN_TEAL(
        id = "ocean_teal",
        titleAr = "8. أزرق بحري عميق وتركواز سماوي",
        darkBg = Color(0xFF081C2E),
        darkSurface = Color(0xFF0F2942),
        darkCard = Color(0xFF173A5C),
        darkPrimary = Color(0xFF38BDF8),
        darkSecondary = Color(0xFF40D6B4),
        lightBg = Color(0xFFF0F9FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0369A1),
        lightSecondary = Color(0xFF0F9D84)
    ),
    CRIMSON_BURGUNDY(
        id = "crimson_burgundy",
        titleAr = "9. عنابي كحلي وأصفر دافئ",
        darkBg = Color(0xFF1E0D1B),
        darkSurface = Color(0xFF2B1427),
        darkCard = Color(0xFF3B1B35),
        darkPrimary = Color(0xFFFB7185),
        darkSecondary = Color(0xFFFFD54F),
        lightBg = Color(0xFFFFF1F2),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF9F1239),
        lightSecondary = Color(0xFFB45309)
    ),
    SLATE_CYAN_MODERN(
        id = "slate_cyan_modern",
        titleAr = "10. رصاصي كحلي وفيروزي حديث",
        darkBg = Color(0xFF111827),
        darkSurface = Color(0xFF1B2436),
        darkCard = Color(0xFF25324B),
        darkPrimary = Color(0xFF2DD4BF),
        darkSecondary = Color(0xFFFFFFFF),
        lightBg = Color(0xFFF1F5F9),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0F766E),
        lightSecondary = Color(0xFF1E293B)
    ),
    SAPPHIRE_CORAL(
        id = "sapphire_coral",
        titleAr = "11. ياقوتي أزرق ومرجاني دافئ",
        darkBg = Color(0xFF08142B),
        darkSurface = Color(0xFF102042),
        darkCard = Color(0xFF182D59),
        darkPrimary = Color(0xFFFF7F66),
        darkSecondary = Color(0xFF38BDF8),
        lightBg = Color(0xFFF0F5FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF1D4ED8),
        lightSecondary = Color(0xFFE11D48)
    ),
    FOREST_GOLD_LUX(
        id = "forest_gold_lux",
        titleAr = "12. أخضر غابي وذهبي فاخر",
        darkBg = Color(0xFF061712),
        darkSurface = Color(0xFF0C241D),
        darkCard = Color(0xFF133329),
        darkPrimary = Color(0xFFFCD34D),
        darkSecondary = Color(0xFF4ADE80),
        lightBg = Color(0xFFF0FDF4),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF166534),
        lightSecondary = Color(0xFFD97706)
    ),
    GRAPHITE_ROSE_GOLD(
        id = "graphite_rose_gold",
        titleAr = "13. جرافيتي أنيق ووردي ذهبي",
        darkBg = Color(0xFF12131A),
        darkSurface = Color(0xFF1C1E29),
        darkCard = Color(0xFF272A38),
        darkPrimary = Color(0xFFF472B6),
        darkSecondary = Color(0xFFFDE047),
        lightBg = Color(0xFFFDF2F8),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF9D174D),
        lightSecondary = Color(0xFF475569)
    ),
    DESERT_AMBER_NAVY(
        id = "desert_amber_navy",
        titleAr = "14. كحلي صحراوي وعنبري لامع",
        darkBg = Color(0xFF0D1321),
        darkSurface = Color(0xFF162036),
        darkCard = Color(0xFF1E2D4D),
        darkPrimary = Color(0xFFFB923C),
        darkSecondary = Color(0xFF2DD4BF),
        lightBg = Color(0xFFFFF7ED),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFFC2410C),
        lightSecondary = Color(0xFF1E3A8A)
    ),
    PEARL_INDIGO_LIME(
        id = "pearl_indigo_lime",
        titleAr = "15. نيلي لؤلؤي وليموني مشرق",
        darkBg = Color(0xFF0B1026),
        darkSurface = Color(0xFF131B3D),
        darkCard = Color(0xFF1C2754),
        darkPrimary = Color(0xFFA3E635),
        darkSecondary = Color(0xFF60A5FA),
        lightBg = Color(0xFFF7FEE7),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF3F6212),
        lightSecondary = Color(0xFF1E40AF)
    ),
    STYLE_1_DARK_STUDY(
        id = "style_1_dark_study",
        titleAr = "نمط إضافي 1: كحلي داكن وبنفسجي",
        darkBg = Color(0xFF131926),
        darkSurface = Color(0xFF1C2334),
        darkCard = Color(0xFF242E44),
        darkPrimary = Color(0xFF7B5CFF),
        darkSecondary = Color(0xFFFACC15),
        lightBg = Color(0xFFFFFFFF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF5B3CE6),
        lightSecondary = Color(0xFFEAB308)
    ),
    STYLE_2_ROYAL_TIMELINE(
        id = "style_2_royal_timeline",
        titleAr = "نمط إضافي 2: بنفسجي وأبيض",
        darkBg = Color(0xFF1B164A),
        darkSurface = Color(0xFF28216B),
        darkCard = Color(0xFF3F359C),
        darkPrimary = Color(0xFFA79FFF),
        darkSecondary = Color(0xFF38BDF8),
        lightBg = Color(0xFFFFFFFF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF5B4FE8),
        lightSecondary = Color(0xFF3B82F6)
    ),
    STYLE_3_GRADIENT_ANALYTICS(
        id = "style_3_gradient_analytics",
        titleAr = "نمط إضافي 3: أزرق متدرج وفيروزي",
        darkBg = Color(0xFF161347),
        darkSurface = Color(0xFF221C69),
        darkCard = Color(0xFF2F288A),
        darkPrimary = Color(0xFF38BDF8),
        darkSecondary = Color(0xFF2DD4BF),
        lightBg = Color(0xFFFFFFFF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF3B28CC),
        lightSecondary = Color(0xFF06B6D4)
    ),
    STYLE_4_CURVED_CORAL(
        id = "style_4_curved_coral",
        titleAr = "نمط إضافي 4: كحلي ومرجاني",
        darkBg = Color(0xFF182038),
        darkSurface = Color(0xFF212B4A),
        darkCard = Color(0xFF2B385E),
        darkPrimary = Color(0xFFF4435E),
        darkSecondary = Color(0xFF60A5FA),
        lightBg = Color(0xFFF4F6FB),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF182038),
        lightSecondary = Color(0xFFF4435E)
    );

    val isImageDesignPreset: Boolean
        get() = this == DESIGN_7_MIDNIGHT_NEON ||
            this == DESIGN_9_ROYAL_PURPLE_PEARL ||
            this == DESIGN_5_OCEAN_CRYSTAL ||
            this == DESIGN_6_PURE_SKY_BLUE

    val isScreenshotStyle: Boolean
        get() = isImageDesignPreset ||
            this == STYLE_1_DARK_STUDY ||
            this == STYLE_2_ROYAL_TIMELINE ||
            this == STYLE_3_GRADIENT_ANALYTICS ||
            this == STYLE_4_CURVED_CORAL

    val prefersDarkByDefault: Boolean
        get() = when (this) {
            DESIGN_9_ROYAL_PURPLE_PEARL, DESIGN_5_OCEAN_CRYSTAL, DESIGN_6_PURE_SKY_BLUE -> false
            else -> true
        }
}

data class SubjectColorSpec(
    val darkBg: Color,
    val darkSurface: Color,
    val darkCard: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val lightBg: Color,
    val lightSurface: Color,
    val lightPrimary: Color,
    val lightSecondary: Color
)

fun com.example.model.SubjectSpecialty.paletteSpec(): SubjectColorSpec = when (this) {
    com.example.model.SubjectSpecialty.BIOLOGY -> SubjectColorSpec(
        darkBg = Color(0xFF051A13),
        darkSurface = Color(0xFF09261D),
        darkCard = Color(0xFF0F3629),
        darkPrimary = Color(0xFF34D399),
        darkSecondary = Color(0xFF6EE7B7),
        lightBg = Color(0xFFECFDF5),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF047857),
        lightSecondary = Color(0xFF10B981)
    )
    com.example.model.SubjectSpecialty.PHYSICS -> SubjectColorSpec(
        darkBg = Color(0xFF0B192C),
        darkSurface = Color(0xFF112640),
        darkCard = Color(0xFF183454),
        darkPrimary = Color(0xFF40D6B4),
        darkSecondary = Color(0xFF38BDF8),
        lightBg = Color(0xFFF0F9FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0F766E),
        lightSecondary = Color(0xFF0284C7)
    )
    com.example.model.SubjectSpecialty.MATH -> SubjectColorSpec(
        darkBg = Color(0xFF10152B),
        darkSurface = Color(0xFF171E3D),
        darkCard = Color(0xFF1F284F),
        darkPrimary = Color(0xFFFBBF24),
        darkSecondary = Color(0xFF818CF8),
        lightBg = Color(0xFFF5F7FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF4338CA),
        lightSecondary = Color(0xFFD97706)
    )
    com.example.model.SubjectSpecialty.CHEMISTRY -> SubjectColorSpec(
        darkBg = Color(0xFF160E24),
        darkSurface = Color(0xFF201536),
        darkCard = Color(0xFF2C1D4A),
        darkPrimary = Color(0xFFC084FC),
        darkSecondary = Color(0xFF38BDF8),
        lightBg = Color(0xFFFAF5FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF7E22CE),
        lightSecondary = Color(0xFF0284C7)
    )
    com.example.model.SubjectSpecialty.ARABIC -> SubjectColorSpec(
        darkBg = Color(0xFF210E17),
        darkSurface = Color(0xFF2E1420),
        darkCard = Color(0xFF3D1B2B),
        darkPrimary = Color(0xFFFB7185),
        darkSecondary = Color(0xFFFBBF24),
        lightBg = Color(0xFFFFF5F7),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF9F1239),
        lightSecondary = Color(0xFFB45309)
    )
    com.example.model.SubjectSpecialty.ENGLISH -> SubjectColorSpec(
        darkBg = Color(0xFF0B162C),
        darkSurface = Color(0xFF112140),
        darkCard = Color(0xFF172E57),
        darkPrimary = Color(0xFF60A5FA),
        darkSecondary = Color(0xFF22D3EE),
        lightBg = Color(0xFFEFF6FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF1D4ED8),
        lightSecondary = Color(0xFF0891B2)
    )
    com.example.model.SubjectSpecialty.SOCIAL_STUDIES -> SubjectColorSpec(
        darkBg = Color(0xFF1B150B),
        darkSurface = Color(0xFF271E10),
        darkCard = Color(0xFF362916),
        darkPrimary = Color(0xFFF59E0B),
        darkSecondary = Color(0xFFA3E635),
        lightBg = Color(0xFFFFFBEB),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF92400E),
        lightSecondary = Color(0xFF65A30D)
    )
    com.example.model.SubjectSpecialty.ISLAMIC -> SubjectColorSpec(
        darkBg = Color(0xFF081C11),
        darkSurface = Color(0xFF0F2A1A),
        darkCard = Color(0xFF163925),
        darkPrimary = Color(0xFF4ADE80),
        darkSecondary = Color(0xFFFACC15),
        lightBg = Color(0xFFF0FDF4),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF15803D),
        lightSecondary = Color(0xFFCA8A04)
    )
    com.example.model.SubjectSpecialty.COMPUTER -> SubjectColorSpec(
        darkBg = Color(0xFF071624),
        darkSurface = Color(0xFF0E2236),
        darkCard = Color(0xFF142F4A),
        darkPrimary = Color(0xFF22D3EE),
        darkSecondary = Color(0xFFA78BFA),
        lightBg = Color(0xFFECFEFF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF0E7490),
        lightSecondary = Color(0xFF6D28D9)
    )
    com.example.model.SubjectSpecialty.SPORTS -> SubjectColorSpec(
        darkBg = Color(0xFF1E110E),
        darkSurface = Color(0xFF2B1914),
        darkCard = Color(0xFF3A221B),
        darkPrimary = Color(0xFFFB923C),
        darkSecondary = Color(0xFF4ADE80),
        lightBg = Color(0xFFFFF7ED),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFFC2410C),
        lightSecondary = Color(0xFF15803D)
    )
    com.example.model.SubjectSpecialty.GENERAL -> SubjectColorSpec(
        darkBg = Color(0xFF0A1229),
        darkSurface = Color(0xFF111D3D),
        darkCard = Color(0xFF192A56),
        darkPrimary = Color(0xFFFFD54F),
        darkSecondary = Color(0xFF40D6B4),
        lightBg = Color(0xFFEEF2FF),
        lightSurface = Color(0xFFFFFFFF),
        lightPrimary = Color(0xFF1E296B),
        lightSecondary = Color(0xFF0F766E)
    )
}
