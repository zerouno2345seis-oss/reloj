package com.quran.watch8.data.model

/**
 * Presets focused on Argentina, especially Buenos Aires Province + CABA.
 * Default calculation method for Argentina: ISNA (widely used).
 */
data class LocationPreset(
    val id: String,
    val nameAr: String,
    val nameEs: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "America/Argentina/Buenos_Aires",
    val isProvinceBuenosAires: Boolean = false
)

object ArgentinaLocations {

    // Ciudad Autónoma de Buenos Aires (CABA)
    val BUENOS_AIRES_CABA = LocationPreset(
        id = "ba_caba",
        nameAr = "بوينس آيرس (العاصمة)",
        nameEs = "Buenos Aires (CABA)",
        nameEn = "Buenos Aires City",
        latitude = -34.6037,
        longitude = -58.3816,
        isProvinceBuenosAires = false
    )

    // Provincia de Buenos Aires – major cities
    val LA_PLATA = LocationPreset(
        id = "ba_laplata",
        nameAr = "لا بلاتا (محافظة بوينس آيرس)",
        nameEs = "La Plata",
        nameEn = "La Plata",
        latitude = -34.9215,
        longitude = -57.9545,
        isProvinceBuenosAires = true
    )

    val MAR_DEL_PLATA = LocationPreset(
        id = "ba_mardelplata",
        nameAr = "مار دل بلاتا",
        nameEs = "Mar del Plata",
        nameEn = "Mar del Plata",
        latitude = -38.0055,
        longitude = -57.5426,
        isProvinceBuenosAires = true
    )

    val BAHIA_BLANCA = LocationPreset(
        id = "ba_bahiablanca",
        nameAr = "باهيا بلانكا",
        nameEs = "Bahía Blanca",
        nameEn = "Bahia Blanca",
        latitude = -38.7183,
        longitude = -62.2663,
        isProvinceBuenosAires = true
    )

    val TIGRE = LocationPreset(
        id = "ba_tigre",
        nameAr = "تيغري",
        nameEs = "Tigre",
        nameEn = "Tigre",
        latitude = -34.4260,
        longitude = -58.5796,
        isProvinceBuenosAires = true
    )

    val QUILMES = LocationPreset(
        id = "ba_quilmes",
        nameAr = "كيلمس",
        nameEs = "Quilmes",
        nameEn = "Quilmes",
        latitude = -34.7290,
        longitude = -58.2636,
        isProvinceBuenosAires = true
    )

    val LOMAS_DE_ZAMORA = LocationPreset(
        id = "ba_lomas",
        nameAr = "لوماس دي زامورا",
        nameEs = "Lomas de Zamora",
        nameEn = "Lomas de Zamora",
        latitude = -34.7600,
        longitude = -58.4000,
        isProvinceBuenosAires = true
    )

    // Other major Argentine cities for convenience
    val CORDOBA = LocationPreset(
        id = "ar_cordoba",
        nameAr = "قرطبة",
        nameEs = "Córdoba",
        nameEn = "Cordoba",
        latitude = -31.4201,
        longitude = -64.1888,
        isProvinceBuenosAires = false
    )

    val ROSARIO = LocationPreset(
        id = "ar_rosario",
        nameAr = "روزاريو",
        nameEs = "Rosario",
        nameEn = "Rosario",
        latitude = -32.9442,
        longitude = -60.6505,
        isProvinceBuenosAires = false
    )

    val MENDOZA = LocationPreset(
        id = "ar_mendoza",
        nameAr = "مندوزا",
        nameEs = "Mendoza",
        nameEn = "Mendoza",
        latitude = -32.8895,
        longitude = -68.8458,
        isProvinceBuenosAires = false
    )

    val allPresets: List<LocationPreset> = listOf(
        BUENOS_AIRES_CABA,
        LA_PLATA,
        MAR_DEL_PLATA,
        BAHIA_BLANCA,
        TIGRE,
        QUILMES,
        LOMAS_DE_ZAMORA,
        CORDOBA,
        ROSARIO,
        MENDOZA
    )

    val buenosAiresProvince: List<LocationPreset> = allPresets.filter { it.isProvinceBuenosAires || it.id == "ba_caba" }

    fun findById(id: String): LocationPreset? = allPresets.find { it.id == id }

    // Default for Argentina users
    val DEFAULT = BUENOS_AIRES_CABA
}
