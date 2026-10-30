package com.walkory.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.walkory.app.data.local.durableSnapshots
import com.walkory.app.data.local.storageResult
import com.walkory.app.domain.DomainResult
import com.walkory.app.domain.Preferences
import com.walkory.app.domain.SettingsRepository
import java.io.IOException
import androidx.datastore.preferences.core.Preferences as StoredPreferences

internal class DataStoreSettingsRepository(private val store: DataStore<StoredPreferences>) : SettingsRepository {
    override fun observe() = store.data.durableSnapshots(::decode)

    override suspend fun update(transform: (Preferences) -> Preferences): DomainResult<Preferences> = storageResult {
        val saved = store.updateData { previous -> encode(transform(decode(previous)), previous.toMutablePreferences()) }
        DomainResult.Success(decode(saved))
    }

    override suspend fun reset(): DomainResult<Preferences> = storageResult {
        DomainResult.Success(decode(store.updateData { emptyPreferences() }))
    }
}

private object Keys {
    val language = stringPreferencesKey("language")
    val theme = stringPreferencesKey("theme")
    val accent = stringPreferencesKey("accent")
    val dynamicColors = booleanPreferencesKey("dynamic_colors")
    val textScale = stringPreferencesKey("text_scale")
    val higherContrast = booleanPreferencesKey("higher_contrast")
    val reducedMotion = booleanPreferencesKey("reduced_motion")
    val pdfPhotoQuality = stringPreferencesKey("pdf_photo_quality")
    val defaultInspector = stringPreferencesKey("default_inspector")
}

private fun decode(stored: StoredPreferences): Preferences = try {
    val defaults = Preferences(dynamicColors = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
    Preferences(
        stored.enum(Keys.language, defaults.language), stored.enum(Keys.theme, defaults.theme),
        stored.enum(Keys.accent, defaults.accent), stored[Keys.dynamicColors] ?: defaults.dynamicColors,
        stored.enum(Keys.textScale, defaults.textScale), stored[Keys.higherContrast] ?: defaults.higherContrast,
        stored[Keys.reducedMotion] ?: defaults.reducedMotion,
        stored.enum(Keys.pdfPhotoQuality, defaults.pdfPhotoQuality), stored[Keys.defaultInspector],
    )
} catch (error: IllegalArgumentException) {
    throw IOException("Unsupported stored preference value", error)
} catch (error: ClassCastException) {
    throw IOException("Invalid stored preference type", error)
}

private inline fun <reified T : Enum<T>> StoredPreferences.enum(key: StoredPreferences.Key<String>, default: T): T =
    this[key]?.let { enumValueOf<T>(it) } ?: default

private fun encode(values: Preferences, target: MutablePreferences): StoredPreferences = target.apply {
    this[Keys.language] = values.language.name
    this[Keys.theme] = values.theme.name
    this[Keys.accent] = values.accent.name
    this[Keys.dynamicColors] = values.dynamicColors
    this[Keys.textScale] = values.textScale.name
    this[Keys.higherContrast] = values.higherContrast
    this[Keys.reducedMotion] = values.reducedMotion
    this[Keys.pdfPhotoQuality] = values.pdfPhotoQuality.name
    if (values.defaultInspector == null) remove(Keys.defaultInspector) else this[Keys.defaultInspector] = values.defaultInspector
}
