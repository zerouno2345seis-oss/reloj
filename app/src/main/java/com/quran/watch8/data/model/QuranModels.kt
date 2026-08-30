package com.quran.watch8.data.model

import com.google.gson.annotations.SerializedName

/**
 * Models for the Holy Quran data.
 * Uses Uthmani Hafs script from open source Quran API.
 */

data class QuranResponse(
    @SerializedName("quran") val quran: List<Ayah>
)

data class Ayah(
    @SerializedName("chapter") val chapter: Int,
    @SerializedName("verse") val verse: Int,
    @SerializedName("text") val text: String
)

data class SurahInfo(
    val number: Int,
    val nameAr: String,
    val nameEn: String,
    val versesCount: Int,
    val revelation: String // Mecca / Medina
)

data class Bookmark(
    val id: String = "${System.currentTimeMillis()}",
    val surah: Int,
    val ayah: Int,
    val textSnippet: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)

data class SavedLocation(
    val id: String = "${System.currentTimeMillis()}",
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val type: LocationType = LocationType.IMPORTANT
)

enum class LocationType {
    CAR, IMPORTANT, MOSQUE, OTHER
}

data class VoiceNote(
    val id: String = "${System.currentTimeMillis()}",
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Standard Surah metadata (hardcoded for offline & speed).
 * Arabic names + English + verse counts.
 */
object SurahMetadata {
    val surahs: List<SurahInfo> = listOf(
        SurahInfo(1, "الفاتحة", "Al-Fatiha", 7, "Mecca"),
        SurahInfo(2, "البقرة", "Al-Baqarah", 286, "Medina"),
        SurahInfo(3, "آل عمران", "Aal-E-Imran", 200, "Medina"),
        SurahInfo(4, "النساء", "An-Nisa", 176, "Medina"),
        SurahInfo(5, "المائدة", "Al-Ma'idah", 120, "Medina"),
        SurahInfo(6, "الأنعام", "Al-An'am", 165, "Mecca"),
        SurahInfo(7, "الأعراف", "Al-A'raf", 206, "Mecca"),
        SurahInfo(8, "الأنفال", "Al-Anfal", 75, "Medina"),
        SurahInfo(9, "التوبة", "At-Tawbah", 129, "Medina"),
        SurahInfo(10, "يونس", "Yunus", 109, "Mecca"),
        SurahInfo(11, "هود", "Hud", 123, "Mecca"),
        SurahInfo(12, "يوسف", "Yusuf", 111, "Mecca"),
        SurahInfo(13, "الرعد", "Ar-Ra'd", 43, "Medina"),
        SurahInfo(14, "إبراهيم", "Ibrahim", 52, "Mecca"),
        SurahInfo(15, "الحجر", "Al-Hijr", 99, "Mecca"),
        SurahInfo(16, "النحل", "An-Nahl", 128, "Mecca"),
        SurahInfo(17, "الإسراء", "Al-Isra", 111, "Mecca"),
        SurahInfo(18, "الكهف", "Al-Kahf", 110, "Mecca"),
        SurahInfo(19, "مريم", "Maryam", 98, "Mecca"),
        SurahInfo(20, "طه", "Ta-Ha", 135, "Mecca"),
        SurahInfo(21, "الأنبياء", "Al-Anbiya", 112, "Mecca"),
        SurahInfo(22, "الحج", "Al-Hajj", 78, "Medina"),
        SurahInfo(23, "المؤمنون", "Al-Mu'minun", 118, "Mecca"),
        SurahInfo(24, "النور", "An-Nur", 64, "Medina"),
        SurahInfo(25, "الفرقان", "Al-Furqan", 77, "Mecca"),
        SurahInfo(26, "الشعراء", "Ash-Shu'ara", 227, "Mecca"),
        SurahInfo(27, "النمل", "An-Naml", 93, "Mecca"),
        SurahInfo(28, "القصص", "Al-Qasas", 88, "Mecca"),
        SurahInfo(29, "العنكبوت", "Al-Ankabut", 69, "Mecca"),
        SurahInfo(30, "الروم", "Ar-Rum", 60, "Mecca"),
        SurahInfo(31, "لقمان", "Luqman", 34, "Mecca"),
        SurahInfo(32, "السجدة", "As-Sajda", 30, "Mecca"),
        SurahInfo(33, "الأحزاب", "Al-Ahzab", 73, "Medina"),
        SurahInfo(34, "سبأ", "Saba", 54, "Mecca"),
        SurahInfo(35, "فاطر", "Fatir", 45, "Mecca"),
        SurahInfo(36, "يس", "Ya-Sin", 83, "Mecca"),
        SurahInfo(37, "الصافات", "As-Saffat", 182, "Mecca"),
        SurahInfo(38, "ص", "Sad", 88, "Mecca"),
        SurahInfo(39, "الزمر", "Az-Zumar", 75, "Mecca"),
        SurahInfo(40, "غافر", "Ghafir", 85, "Mecca"),
        SurahInfo(41, "فصلت", "Fussilat", 54, "Mecca"),
        SurahInfo(42, "الشورى", "Ash-Shura", 53, "Mecca"),
        SurahInfo(43, "الزخرف", "Az-Zukhruf", 89, "Mecca"),
        SurahInfo(44, "الدخان", "Ad-Dukhan", 59, "Mecca"),
        SurahInfo(45, "الجاثية", "Al-Jathiya", 37, "Mecca"),
        SurahInfo(46, "الأحقاف", "Al-Ahqaf", 35, "Mecca"),
        SurahInfo(47, "محمد", "Muhammad", 38, "Medina"),
        SurahInfo(48, "الفتح", "Al-Fath", 29, "Medina"),
        SurahInfo(49, "الحجرات", "Al-Hujurat", 18, "Medina"),
        SurahInfo(50, "ق", "Qaf", 45, "Mecca"),
        SurahInfo(51, "الذاريات", "Adh-Dhariyat", 60, "Mecca"),
        SurahInfo(52, "الطور", "At-Tur", 49, "Mecca"),
        SurahInfo(53, "النجم", "An-Najm", 62, "Mecca"),
        SurahInfo(54, "القمر", "Al-Qamar", 55, "Mecca"),
        SurahInfo(55, "الرحمن", "Ar-Rahman", 78, "Medina"),
        SurahInfo(56, "الواقعة", "Al-Waqi'a", 96, "Mecca"),
        SurahInfo(57, "الحديد", "Al-Hadid", 29, "Medina"),
        SurahInfo(58, "المجادلة", "Al-Mujadila", 22, "Medina"),
        SurahInfo(59, "الحشر", "Al-Hashr", 24, "Medina"),
        SurahInfo(60, "الممتحنة", "Al-Mumtahina", 13, "Medina"),
        SurahInfo(61, "الصف", "As-Saff", 14, "Medina"),
        SurahInfo(62, "الجمعة", "Al-Jumu'a", 11, "Medina"),
        SurahInfo(63, "المنافقون", "Al-Munafiqun", 11, "Medina"),
        SurahInfo(64, "التغابن", "At-Taghabun", 18, "Medina"),
        SurahInfo(65, "الطلاق", "At-Talaq", 12, "Medina"),
        SurahInfo(66, "التحريم", "At-Tahrim", 12, "Medina"),
        SurahInfo(67, "الملك", "Al-Mulk", 30, "Mecca"),
        SurahInfo(68, "القلم", "Al-Qalam", 52, "Mecca"),
        SurahInfo(69, "الحاقة", "Al-Haqqa", 52, "Mecca"),
        SurahInfo(70, "المعارج", "Al-Ma'arij", 44, "Mecca"),
        SurahInfo(71, "نوح", "Nuh", 28, "Mecca"),
        SurahInfo(72, "الجن", "Al-Jinn", 28, "Mecca"),
        SurahInfo(73, "المزمل", "Al-Muzzammil", 20, "Mecca"),
        SurahInfo(74, "المدثر", "Al-Muddaththir", 56, "Mecca"),
        SurahInfo(75, "القيامة", "Al-Qiyama", 40, "Mecca"),
        SurahInfo(76, "الإنسان", "Al-Insan", 31, "Medina"),
        SurahInfo(77, "المرسلات", "Al-Mursalat", 50, "Mecca"),
        SurahInfo(78, "النبأ", "An-Naba", 40, "Mecca"),
        SurahInfo(79, "النازعات", "An-Nazi'at", 46, "Mecca"),
        SurahInfo(80, "عبس", "Abasa", 42, "Mecca"),
        SurahInfo(81, "التكوير", "At-Takwir", 29, "Mecca"),
        SurahInfo(82, "الانفطار", "Al-Infitar", 19, "Mecca"),
        SurahInfo(83, "المطففين", "Al-Mutaffifin", 36, "Mecca"),
        SurahInfo(84, "الانشقاق", "Al-Inshiqaq", 25, "Mecca"),
        SurahInfo(85, "البروج", "Al-Buruj", 22, "Mecca"),
        SurahInfo(86, "الطارق", "At-Tariq", 17, "Mecca"),
        SurahInfo(87, "الأعلى", "Al-A'la", 19, "Mecca"),
        SurahInfo(88, "الغاشية", "Al-Ghashiya", 26, "Mecca"),
        SurahInfo(89, "الفجر", "Al-Fajr", 30, "Mecca"),
        SurahInfo(90, "البلد", "Al-Balad", 20, "Mecca"),
        SurahInfo(91, "الشمس", "Ash-Shams", 15, "Mecca"),
        SurahInfo(92, "الليل", "Al-Layl", 21, "Mecca"),
        SurahInfo(93, "الضحى", "Ad-Duha", 11, "Mecca"),
        SurahInfo(94, "الشرح", "Ash-Sharh", 8, "Mecca"),
        SurahInfo(95, "التين", "At-Tin", 8, "Mecca"),
        SurahInfo(96, "العلق", "Al-Alaq", 19, "Mecca"),
        SurahInfo(97, "القدر", "Al-Qadr", 5, "Mecca"),
        SurahInfo(98, "البينة", "Al-Bayyina", 8, "Medina"),
        SurahInfo(99, "الزلزلة", "Az-Zalzala", 8, "Medina"),
        SurahInfo(100, "العاديات", "Al-Adiyat", 11, "Mecca"),
        SurahInfo(101, "القارعة", "Al-Qari'a", 11, "Mecca"),
        SurahInfo(102, "التكاثر", "At-Takathur", 8, "Mecca"),
        SurahInfo(103, "العصر", "Al-Asr", 3, "Mecca"),
        SurahInfo(104, "الهمزة", "Al-Humaza", 9, "Mecca"),
        SurahInfo(105, "الفيل", "Al-Fil", 5, "Mecca"),
        SurahInfo(106, "قريش", "Quraysh", 4, "Mecca"),
        SurahInfo(107, "الماعون", "Al-Ma'un", 7, "Mecca"),
        SurahInfo(108, "الكوثر", "Al-Kawthar", 3, "Mecca"),
        SurahInfo(109, "الكافرون", "Al-Kafirun", 6, "Mecca"),
        SurahInfo(110, "النصر", "An-Nasr", 3, "Medina"),
        SurahInfo(111, "المسد", "Al-Masad", 5, "Mecca"),
        SurahInfo(112, "الإخلاص", "Al-Ikhlas", 4, "Mecca"),
        SurahInfo(113, "الفلق", "Al-Falaq", 5, "Mecca"),
        SurahInfo(114, "الناس", "An-Nas", 6, "Mecca")
    )

    fun getSurah(number: Int): SurahInfo? = surahs.find { it.number == number }
}
