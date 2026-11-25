package com.example.data

data class QuranAyah(
    val numberInSurah: Int,
    val textUthmani: String
)

data class QuranSurah(
    val id: Int,
    val nameArabic: String,
    val revelationType: String,
    val ayahs: List<QuranAyah>
)

data class MushafPageSegment(
    val surah: QuranSurah,
    val showSurahHeader: Boolean,
    val showBismillah: Boolean,
    val ayahs: List<QuranAyah>
)

data class MushafPage(
    val pageNumber: Int,
    val juzLabel: String,
    val primarySurahId: Int,
    val primarySurahName: String,
    val segments: List<MushafPageSegment>
)

data class DhikrEntry(
    val id: String,
    val title: String,
    val arabicText: String,
    val repeatCount: Int,
    val virtue: String = ""
)

object OfflineQuranData {

    fun toArabicIndicDigits(number: Int): String {
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return number.toString().map { ch ->
            if (ch in '0'..'9') arabicDigits[ch - '0'] else ch
        }.joinToString("")
    }

    fun juzLabelForSurah(surahId: Int): String = when {
        surahId == 1 -> "الجزء ١"
        surahId == 2 -> "الجزء ٣"
        surahId == 18 -> "الجزء ١٥–١٦"
        surahId == 67 -> "الجزء ٢٩"
        else -> "جزء عمّ (٣٠)"
    }

    fun stripArabicTashkeel(input: String): String {
        val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
        return input.replace(diacritics, "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .trim()
    }

    fun searchAyahs(query: String): List<Pair<QuranSurah, QuranAyah>> {
        val norm = stripArabicTashkeel(query)
        if (norm.length < 2) return emptyList()
        val matches = mutableListOf<Pair<QuranSurah, QuranAyah>>()
        for (surah in surahs) {
            for (ayah in surah.ayahs) {
                if (stripArabicTashkeel(ayah.textUthmani).contains(norm, ignoreCase = true) ||
                    stripArabicTashkeel(surah.nameArabic).contains(norm, ignoreCase = true)
                ) {
                    matches += surah to ayah
                }
            }
        }
        return matches
    }

    fun buildMushafPages(): List<MushafPage> {
        val pages = mutableListOf<MushafPage>()
        val maxCharsPerPage = 620
        val maxAyahsPerPage = 14

        var currentSegments = mutableListOf<MushafPageSegment>()
        var currentChars = 0
        var currentAyahCount = 0

        fun flushPage() {
            if (currentSegments.isEmpty()) return
            val firstSurah = currentSegments.first().surah
            pages += MushafPage(
                pageNumber = pages.size + 1,
                juzLabel = juzLabelForSurah(firstSurah.id),
                primarySurahId = firstSurah.id,
                primarySurahName = firstSurah.nameArabic,
                segments = currentSegments.toList()
            )
            currentSegments = mutableListOf()
            currentChars = 0
            currentAyahCount = 0
        }

        for (surah in surahs) {
            // Major standalone surahs start cleanly on a fresh page when previous page already has rich content
            if ((surah.id in listOf(2, 18, 67, 78)) && currentChars > 0) {
                flushPage()
            } else if (currentChars > maxCharsPerPage - 150 || currentAyahCount >= maxAyahsPerPage - 2) {
                flushPage()
            }

            var idx = 0
            var isFirstPartOfSurah = true
            while (idx < surah.ayahs.size) {
                val segAyahs = mutableListOf<QuranAyah>()
                if (isFirstPartOfSurah) {
                    currentChars += 95 // Surah header + Bismillah visual weight
                }
                while (idx < surah.ayahs.size) {
                    val ayah = surah.ayahs[idx]
                    val ayahCost = ayah.textUthmani.length + 10
                    val wouldOverflow = segAyahs.isNotEmpty() &&
                        (currentChars + ayahCost > maxCharsPerPage || currentAyahCount >= maxAyahsPerPage)
                    if (wouldOverflow) break

                    segAyahs += ayah
                    currentChars += ayahCost
                    currentAyahCount += 1
                    idx++
                }
                if (segAyahs.isNotEmpty()) {
                    currentSegments += MushafPageSegment(
                        surah = surah,
                        showSurahHeader = isFirstPartOfSurah,
                        showBismillah = isFirstPartOfSurah && surah.id != 1 && surah.id != 9,
                        ayahs = segAyahs
                    )
                    isFirstPartOfSurah = false
                }
                if (idx < surah.ayahs.size) {
                    flushPage()
                }
            }
        }
        flushPage()
        return pages
    }

    fun pageIndexFor(
        surahId: Int,
        ayahNumber: Int = 1,
        pages: List<MushafPage> = buildMushafPages()
    ): Int {
        val exact = pages.indexOfFirst { p ->
            p.segments.any { seg ->
                seg.surah.id == surahId && seg.ayahs.any { it.numberInSurah == ayahNumber }
            }
        }
        if (exact >= 0) return exact
        val bySurah = pages.indexOfFirst { p -> p.segments.any { it.surah.id == surahId } }
        return if (bySurah >= 0) bySurah else 0
    }

    val tasbeehPresets: List<String> = listOf(
        "سُبْحَانَ اللَّهِ",
        "الْحَمْدُ لِلَّهِ",
        "لَا إِلَهَ إِلَّا اللَّهُ",
        "اللَّهُ أَكْبَرُ",
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
        "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
        "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
        "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ"
    )

    val morningAthkar: List<DhikrEntry> = listOf(
        DhikrEntry(
            "m1",
            "آية الكرسي",
            "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            1,
            "من قرأها حين يصبح أُجير من الجن حتى يمسي."
        ),
        DhikrEntry(
            "m2",
            "المعوذات (الإخلاص والفلق والناس)",
            "قُلْ هُوَ اللَّهُ أَحَدٌ ۝ قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝ قُلْ أَعُوذُ بِرَبِّ النَّاسِ",
            3,
            "تكفيك من كل شيء."
        ),
        DhikrEntry(
            "m3",
            "سيد الاستغفار",
            "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
            1,
            "من قالها موقناً بها فمات من يومه دخل الجنة."
        ),
        DhikrEntry(
            "m4",
            "ذكر الصباح",
            "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
            1,
            "بداية طيبة ليوم دراسي مبارك."
        ),
        DhikrEntry(
            "m5",
            "دعاء العلم النافع",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً.",
            1,
            "يُقال صباحاً قبل التوجه للمدرسة."
        ),
        DhikrEntry(
            "m6",
            "الحفظ من كل شر",
            "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.",
            3,
            "لم يضره شيء في يومه."
        ),
        DhikrEntry(
            "m7",
            "التسبيح المضاعف",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ.",
            3,
            "أجر عظيم بكلمات يسيرة."
        ),
        DhikrEntry(
            "m8",
            "التوكل على الله",
            "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.",
            7,
            "كفاه الله ما أهمّه."
        )
    )

    val eveningAthkar: List<DhikrEntry> = listOf(
        DhikrEntry(
            "e1",
            "آية الكرسي",
            "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            1,
            "حفظ من الله طوال الليل."
        ),
        DhikrEntry(
            "e2",
            "ذكر المساء",
            "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
            1
        ),
        DhikrEntry(
            "e3",
            "دعاء العافية في المساء",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي.",
            1
        ),
        DhikrEntry(
            "e4",
            "التحصين المسائي",
            "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.",
            3
        ),
        DhikrEntry(
            "e5",
            "خواتيم سورة البقرة",
            "آمَنَ الرَّسُولُ بِمَا أُنزِلَ إِلَيْهِ مِن رَّبِّهِ وَالْمُؤْمِنُونَ ۚ كُلٌّ آمَنَ بِاللَّهِ وَمَلَائِكَتِهِ وَكُتُبِهِ وَرُسُلِهِ لَا نُفَرِّقُ بَيْنَ أَحَدٍ مِّن رُّسُلِهِ ۚ وَقَالُوا سَمِعْنَا وَأَطَعْنَا ۖ غُفْرَانَكَ رَبَّنَا وَإِلَيْكَ الْمَصِيرُ",
            1,
            "من قرأهما في ليلة كفتاه."
        ),
        DhikrEntry(
            "e6",
            "التوكل",
            "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.",
            7
        )
    )

    val surahs: List<QuranSurah> = listOf(
        QuranSurah(
            1, "سورة الفاتحة", "مكية", listOf(
                QuranAyah(1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"),
                QuranAyah(2, "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"),
                QuranAyah(3, "الرَّحْمَٰنِ الرَّحِيمِ"),
                QuranAyah(4, "مَالِكِ يَوْمِ الدِّينِ"),
                QuranAyah(5, "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ"),
                QuranAyah(6, "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ"),
                QuranAyah(7, "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ")
            )
        ),
        QuranSurah(
            2, "آيات مختارة من سورة البقرة (آية الكرسي والخواتيم)", "مدنية", listOf(
                QuranAyah(255, "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ"),
                QuranAyah(285, "آمَنَ الرَّسُولُ بِمَا أُنزِلَ إِلَيْهِ مِن رَّبِّهِ وَالْمُؤْمِنُونَ ۚ كُلٌّ آمَنَ بِاللَّهِ وَمَلَائِكَتِهِ وَكُتُبِهِ وَرُسُلِهِ لَا نُفَرِّقُ بَيْنَ أَحَدٍ مِّن رُّسُلِهِ ۚ وَقَالُوا سَمِعْنَا وَأَطَعْنَا ۖ غُفْرَانَكَ رَبَّنَا وَإِلَيْكَ الْمَصِيرُ"),
                QuranAyah(286, "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا ۚ لَهَا مَا كَسَبَتْ وَعَلَيْهَا مَا اكْتَسَبَتْ ۗ رَبَّنَا لَا تُؤَاخِذْنَا إِن نَّسِينَا أَوْ أَخْطَأْنَا ۚ رَبَّنَا وَلَا تَحْمِلْ عَلَيْنَا إِصْرًا كَمَا حَمَلْتَهُ عَلَى الَّذِينَ مِن قَبْلِنَا ۚ رَبَّنَا وَلَا تُحَمِّلْنَا مَا لَا طَاقَةَ لَنَا بِهِ ۖ وَاعْفُ عَنَّا وَاغْفِرْ لَنَا وَارْحَمْنَا ۚ أَنتَ مَوْلَانَا فَانصُرْنَا عَلَى الْقَوْمِ الْكَافِرِينَ")
            )
        ),
        QuranSurah(
            18, "أوائل وأواخر سورة الكهف", "مكية", listOf(
                QuranAyah(1, "الْحَمْدُ لِلَّهِ الَّذِي أَنزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ وَلَمْ يَجْعَل لَّهُ عِوَجَا"),
                QuranAyah(2, "قَيِّمًا لِّيُنذِرَ بَأْسًا شَدِيدًا مِّن لَّدُنْهُ وَيُبَشِّرَ الْمُؤْمِنِينَ الَّذِينَ يَعْمَلُونَ الصَّالِحَاتِ أَنَّ لَهُمْ أَجْرًا حَسَنًا"),
                QuranAyah(3, "مَّاكِثِينَ فِيهِ أَبَدًا"),
                QuranAyah(4, "وَيُنذِرَ الَّذِينَ قَالُوا اتَّخَذَ اللَّهُ وَلَدًا"),
                QuranAyah(5, "مَّا لَهُم بِهِ مِنْ عِلْمٍ وَلَا لِآبَائِهِمْ ۚ كَبُرَتْ كَلِمَةً تَخْرُجُ مِنْ أَفْوَاهِهِمْ ۚ إِن يَقُولُونَ إِلَّا كَذِبًا"),
                QuranAyah(6, "فَلَعَلَّكَ بَاخِعٌ نَّفْسَكَ عَلَىٰ آثَارِهِمْ إِن لَّمْ يُؤْمِنُوا بِهَٰذَا الْحَدِيثِ أَسَفًا"),
                QuranAyah(7, "إِنَّا جَعَلْنَا مَا عَلَى الْأَرْضِ زِينَةً لَّهَا لِنَبْلُوَهُمْ أَيُّهُمْ أَحْسَنُ عَمَلًا"),
                QuranAyah(8, "وَإِنَّا لَجَاعِلُونَ مَا عَلَيْهَا صَعِيدًا جُرُزًا"),
                QuranAyah(9, "أَمْ حَسِبْتَ أَنَّ أَصْحَابَ الْكَهْفِ وَالرَّقِيمِ كَانُوا مِنْ آيَاتِنَا عَجَبًا"),
                QuranAyah(10, "إِذْ أَوَى الْفِتْيَةُ إِلَى الْكَهْفِ فَقَالُوا رَبَّنَا آتِنَا مِن لَّدُنكَ رَحْمَةً وَهَيِّئْ لَنَا مِنْ أَمْرِنَا رَشَدًا"),
                QuranAyah(107, "إِنَّ الَّذِينَ آمَنُوا وَعَمِلُوا الصَّالِحَاتِ كَانَتْ لَهُمْ جَنَّاتُ الْفِرْدَوْسِ نُزُلًا"),
                QuranAyah(108, "خَالِدِينَ فِيهَا لَا يَبْغُونَ عَنْهَا حِوَلًا"),
                QuranAyah(109, "قُل لَّوْ كَانَ الْبَحْرُ مِدَادًا لِّكَلِمَاتِ رَبِّي لَنَفِدَ الْبَحْرُ قَبْلَ أَن تَنفَدَ كَلِمَاتُ رَبِّي وَلَوْ جِئْنَا بِمِثْلِهِ مَدَدًا"),
                QuranAyah(110, "قُلْ إِنَّمَا أَنَا بَشَرٌ مِّثْلُكُمْ يُوحَىٰ إِلَيَّ أَنَّمَا إِلَٰهُكُمْ إِلَٰهٌ وَاحِدٌ ۖ فَمَن كَانَ يَرْجُو لِقَاءَ رَبِّهِ فَلْيَعْمَلْ عَمَلًا صَالِحًا وَلَا يُشْرِكْ بِعِبَادَةِ رَبِّهِ أَحَدًا")
            )
        )
    ) + OfflineQuranJuzAmmaExt.surahs67To101 + listOf(
        QuranSurah(
            102, "سورة التكاثر", "مكية", listOf(
                QuranAyah(1, "أَلْهَاكُمُ التَّكَاثُرُ"),
                QuranAyah(2, "حَتَّىٰ زُرْتُمُ الْمَقَابِرَ"),
                QuranAyah(3, "كَلَّا سَوْفَ تَعْلَمُونَ"),
                QuranAyah(4, "ثُمَّ كَلَّا سَوْفَ تَعْلَمُونَ"),
                QuranAyah(5, "كَلَّا لَوْ تَعْلَمُونَ عِلْمَ الْيَقِينِ"),
                QuranAyah(6, "لَتَرَوُنَّ الْجَحِيمَ"),
                QuranAyah(7, "ثُمَّ لَتَرَوُنَّهَا عَيْنَ الْيَقِينِ"),
                QuranAyah(8, "ثُمَّ لَتُسْأَلُنَّ يَوْمَئِذٍ عَنِ النَّعِيمِ")
            )
        ),
        QuranSurah(
            103, "سورة العصر", "مكية", listOf(
                QuranAyah(1, "وَالْعَصْرِ"),
                QuranAyah(2, "إِنَّ الْإِنسَانَ لَفِي خُسْرٍ"),
                QuranAyah(3, "إِلَّا الَّذِينَ آمَنُوا وَعَمِلُوا الصَّالِحَاتِ وَتَوَاصَوْا بِالْحَقِّ وَتَوَاصَوْا بِالصَّبْرِ")
            )
        ),
        QuranSurah(
            104, "سورة الهمزة", "مكية", listOf(
                QuranAyah(1, "وَيْلٌ لِّكُلِّ هُمَزَةٍ لُّمَزَةٍ"),
                QuranAyah(2, "الَّذِي جَمَعَ مَالًا وَعَدَّدَهُ"),
                QuranAyah(3, "يَحْسَبُ أَنَّ مَالَهُ أَخْلَدَهُ"),
                QuranAyah(4, "كَلَّا ۖ لَيُنبَذَنَّ فِي الْحُطَمَةِ"),
                QuranAyah(5, "وَمَا أَدْرَاكَ مَا الْحُطَمَةُ"),
                QuranAyah(6, "نَارُ اللَّهِ الْمُوقَدَةُ"),
                QuranAyah(7, "الَّتِي تَطَّلِعُ عَلَى الْأَفْئِدَةِ"),
                QuranAyah(8, "إِنَّهَا عَلَيْهِم مُّؤْصَدَةٌ"),
                QuranAyah(9, "فِي عَمَدٍ مُّمَدَّدَةٍ")
            )
        ),
        QuranSurah(
            105, "سورة الفيل", "مكية", listOf(
                QuranAyah(1, "أَلَمْ تَرَ كَيْفَ فَعَلَ رَبُّكَ بِأَصْحَابِ الْفِيلِ"),
                QuranAyah(2, "أَلَمْ يَجْعَلْ كَيْدَهُمْ فِي تَضْلِيلٍ"),
                QuranAyah(3, "وَأَرْسَلَ عَلَيْهِمْ طَيْرًا أَبَابِيلَ"),
                QuranAyah(4, "تَرْمِيهِم بِحِجَارَةٍ مِّن سِجِّيلٍ"),
                QuranAyah(5, "فَجَعَلَهُمْ كَعَصْفٍ مَّأْكُولٍ")
            )
        ),
        QuranSurah(
            106, "سورة قريش", "مكية", listOf(
                QuranAyah(1, "لِإِيلَافِ قُرَيْشٍ"),
                QuranAyah(2, "إِيلَافِهِمْ رِحْلَةَ الشِّتَاءِ وَالصَّيْفِ"),
                QuranAyah(3, "فَلْيَعْبُدُوا رَبَّ هَٰذَا الْبَيْتِ"),
                QuranAyah(4, "الَّذِي أَطْعَمَهُم مِّن جُوعٍ وَآمَنَهُم مِّنْ خَوْفٍ")
            )
        ),
        QuranSurah(
            107, "سورة الماعون", "مكية", listOf(
                QuranAyah(1, "أَرَأَيْتَ الَّذِي يُكَذِّبُ بِالدِّينِ"),
                QuranAyah(2, "فَذَٰلِكَ الَّذِي يَدُعُّ الْيَتِيمَ"),
                QuranAyah(3, "وَلَا يَحُضُّ عَلَىٰ طَعَامِ الْمِسْكِينِ"),
                QuranAyah(4, "فَوَيْلٌ لِّلْمُصَلِّينَ"),
                QuranAyah(5, "الَّذِينَ هُمْ عَن صَلَاتِهِمْ سَاهُونَ"),
                QuranAyah(6, "الَّذِينَ هُمْ يُرَاءُونَ"),
                QuranAyah(7, "وَيَمْنَعُونَ الْمَاعُونَ")
            )
        ),
        QuranSurah(
            108, "سورة الكوثر", "مكية", listOf(
                QuranAyah(1, "إِنَّا أَعْطَيْنَاكَ الْكَوْثَرَ"),
                QuranAyah(2, "فَصَلِّ لِرَبِّكَ وَانْحَرْ"),
                QuranAyah(3, "إِنَّ شَانِئَكَ هُوَ الْأَبْتَرُ")
            )
        ),
        QuranSurah(
            109, "سورة الكافرون", "مكية", listOf(
                QuranAyah(1, "قُلْ يَا أَيُّهَا الْكَافِرُونَ"),
                QuranAyah(2, "لَا أَعْبُدُ مَا تَعْبُدُونَ"),
                QuranAyah(3, "وَلَا أَنتُمْ عَابِدُونَ مَا أَعْبُدُ"),
                QuranAyah(4, "وَلَا أَنَا عَابِدٌ مَّا عَبَدتُّمْ"),
                QuranAyah(5, "وَلَا أَنتُمْ عَابِدُونَ مَا أَعْبُدُ"),
                QuranAyah(6, "لَكُمْ دِينُكُمْ وَلِيَ دِينِ")
            )
        ),
        QuranSurah(
            110, "سورة النصر", "مدنية", listOf(
                QuranAyah(1, "إِذَا جَاءَ نَصْرُ اللَّهِ وَالْفَتْحُ"),
                QuranAyah(2, "وَرَأَيْتَ النَّاسَ يَدْخُلُونَ فِي دِينِ اللَّهِ أَفْوَاجًا"),
                QuranAyah(3, "فَسَبِّحْ بِحَمْدِ رَبِّكَ وَاسْتَغْفِرْهُ ۚ إِنَّهُ كَانَ تَوَّابًا")
            )
        ),
        QuranSurah(
            111, "سورة المسد", "مكية", listOf(
                QuranAyah(1, "تَبَّتْ يَدَا أَبِي لَهَبٍ وَتَبَّ"),
                QuranAyah(2, "مَا أَغْنَىٰ عَنْهُ مَالُهُ وَمَا كَسَبَ"),
                QuranAyah(3, "سَيَصْلَىٰ نَارًا ذَاتَ لَهَبٍ"),
                QuranAyah(4, "وَامْرَأَتُهُ حَمَّالَةَ الْحَطَبِ"),
                QuranAyah(5, "فِي جِيدِهَا حَبْلٌ مِّن مَّسَدٍ")
            )
        ),
        QuranSurah(
            112, "سورة الإخلاص", "مكية", listOf(
                QuranAyah(1, "قُلْ هُوَ اللَّهُ أَحَدٌ"),
                QuranAyah(2, "اللَّهُ الصَّمَدُ"),
                QuranAyah(3, "لَمْ يَلِدْ وَلَمْ يُولَدْ"),
                QuranAyah(4, "وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ")
            )
        ),
        QuranSurah(
            113, "سورة الفلق", "مكية", listOf(
                QuranAyah(1, "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ"),
                QuranAyah(2, "مِن شَرِّ مَا خَلَقَ"),
                QuranAyah(3, "وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ"),
                QuranAyah(4, "وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ"),
                QuranAyah(5, "وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ")
            )
        ),
        QuranSurah(
            114, "سورة الناس", "مكية", listOf(
                QuranAyah(1, "قُلْ أَعُوذُ بِرَبِّ النَّاسِ"),
                QuranAyah(2, "مَلِكِ النَّاسِ"),
                QuranAyah(3, "إِلَٰهِ النَّاسِ"),
                QuranAyah(4, "مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ"),
                QuranAyah(5, "الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ"),
                QuranAyah(6, "مِنَ الْجِنَّةِ وَالنَّاسِ")
            )
        )
    )
}
