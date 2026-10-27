package com.walkory.app.domain

enum class AppLanguage { SYSTEM, RUSSIAN, ENGLISH }
enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class Accent { TEAL, BLUE, GREEN, PURPLE, CORAL, AMBER, MONOCHROME }
enum class AppTextScale(val percent: Int) { STANDARD(100), LARGE(115), EXTRA_LARGE(130) }
enum class PdfPhotoQuality { COMPACT, BALANCED, HIGH }

data class Preferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.SYSTEM,
    val accent: Accent = Accent.TEAL,
    val dynamicColors: Boolean = false,
    val textScale: AppTextScale = AppTextScale.STANDARD,
    val higherContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val pdfPhotoQuality: PdfPhotoQuality = PdfPhotoQuality.BALANCED,
    val defaultInspector: String? = null,
)
