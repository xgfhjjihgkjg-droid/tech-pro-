package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private val Context.uiPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ciphertalk_ui_prefs")

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: LIGHT
    }
}

enum class AppAccentTheme(val label: String) {
    EMERALD("Emerald"),
    OCEAN("Ocean"),
    VIOLET("Violet"),
    AMBER("Amber"),
    ROSE("Rose"),
    GOLD("Gold"),
    SAPPHIRE("Sapphire"),
    TEAL("Teal"),
    BURGUNDY("Burgundy"),
    INDIGO("Indigo"),
    COPPER("Copper"),
    PLUM("Plum"),
    CORAL("Coral"),
    JADE("Jade"),
    GRAPHITE("Graphite"),
    COBALT("Cobalt");

    companion object {
        fun fromRaw(raw: String?): AppAccentTheme =
            entries.firstOrNull { it.name == raw } ?: EMERALD
    }
}

enum class AppFontStyle(val label: String) {
    CLASSIC("Classic"),
    ROUNDED("Rounded"),
    MODERN("Modern"),
    SERIF("Editorial"),
    MONO("Typewriter"),
    SPACE_GROTESK("Grotesk"),
    INTER("Inter"),
    ROBOTO("Roboto"),
    MONTSERRAT("Montserrat"),
    RALEWAY("Raleway"),
    OSWALD("Oswald"),
    PLAYFAIR("Playfair"),
    MERRIWEATHER("Merriweather"),
    PACIFICO("Pacifico"),
    DANCING_SCRIPT("Dancing"),
    CAVEAT("Caveat"),
    BEBAS_NEUE("Bebas"),
    COMFORTAA("Comfortaa"),
    QUICKSAND("Quicksand"),
    FREDOKA("Fredoka"),
    DM_SANS("DM Sans"),
    RUBIK("Rubik"),
    FIRA_CODE("Fira Code"),
    RIGHTEOUS("Righteous"),
    CINZEL("Cinzel");

    companion object {
        fun fromRaw(raw: String?): AppFontStyle =
            entries.firstOrNull { it.name == raw } ?: CLASSIC
    }
}

enum class AppTextScale(val label: String, val multiplier: Float) {
    COMPACT("Small", 0.90f),
    MEDIUM("Default", 1.00f),
    LARGE("Large", 1.12f),
    XLARGE("XL", 1.24f);

    companion object {
        fun fromRaw(raw: String?): AppTextScale =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

data class ThemeSettings(
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val accentTheme: AppAccentTheme = AppAccentTheme.EMERALD,
    val fontStyle: AppFontStyle = AppFontStyle.CLASSIC,
    val textScale: AppTextScale = AppTextScale.MEDIUM,
    val boldText: Boolean = false,
    val chatWallpaperPath: String? = null,
    val wallpaperDim: Float = 0.55f,
    val languageCode: String = "en"
)

class ThemePreferencesRepository(context: Context) {
    private val appContext = context.applicationContext

    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyAccentTheme = stringPreferencesKey("accent_theme")
    private val keyFontStyle = stringPreferencesKey("font_style")
    private val keyTextScale = stringPreferencesKey("text_scale")
    private val keyBoldText = booleanPreferencesKey("bold_text")
    private val keyWallpaperPath = stringPreferencesKey("chat_wallpaper_path")
    private val keyWallpaperDim = floatPreferencesKey("chat_wallpaper_dim")
    private val keyLanguageCode = stringPreferencesKey("app_language_code")

    val themeSettings: Flow<ThemeSettings> = appContext.uiPrefsDataStore.data.map { prefs ->
        val rawPath = prefs[keyWallpaperPath]
        val validPath = rawPath?.takeIf { File(it).exists() }
        ThemeSettings(
            themeMode = AppThemeMode.fromRaw(prefs[keyThemeMode]),
            accentTheme = AppAccentTheme.fromRaw(prefs[keyAccentTheme]),
            fontStyle = AppFontStyle.fromRaw(prefs[keyFontStyle]),
            textScale = AppTextScale.fromRaw(prefs[keyTextScale]),
            boldText = prefs[keyBoldText] ?: false,
            chatWallpaperPath = validPath,
            wallpaperDim = (prefs[keyWallpaperDim] ?: 0.55f).coerceIn(0.15f, 0.85f),
            languageCode = AppLanguage.fromCode(prefs[keyLanguageCode]).code
        )
    }

    suspend fun setLanguageCode(code: String) {
        val normalized = AppLanguage.fromCode(code).code
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyLanguageCode] = normalized
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyThemeMode] = mode.name
        }
    }

    suspend fun setAccentTheme(accent: AppAccentTheme) {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyAccentTheme] = accent.name
        }
    }

    suspend fun setFontStyle(style: AppFontStyle) {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyFontStyle] = style.name
        }
    }

    suspend fun setTextScale(scale: AppTextScale) {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyTextScale] = scale.name
        }
    }

    suspend fun setBoldText(enabled: Boolean) {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyBoldText] = enabled
        }
    }

    suspend fun resetTextStyle() {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyFontStyle] = AppFontStyle.CLASSIC.name
            prefs[keyTextScale] = AppTextScale.MEDIUM.name
            prefs[keyBoldText] = false
        }
    }

    suspend fun setWallpaperDim(dim: Float) {
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs[keyWallpaperDim] = dim.coerceIn(0.15f, 0.85f)
        }
    }

    suspend fun saveGalleryWallpaper(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image selected" }

            var sampleSize = 1
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: error("Could not decode gallery photo")

            val scale = 1440f / max(decoded.width, decoded.height).toFloat()
            val finalBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            // Clean up any old wallpaper files
            appContext.filesDir.listFiles()
                ?.filter { it.name.startsWith("ciphertalk_wallpaper_") }
                ?.forEach { it.delete() }

            val outFile = File(appContext.filesDir, "ciphertalk_wallpaper_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            if (finalBitmap !== decoded) decoded.recycle()
            finalBitmap.recycle()

            appContext.uiPrefsDataStore.edit { prefs ->
                prefs[keyWallpaperPath] = outFile.absolutePath
            }
            outFile.absolutePath
        }
    }

    suspend fun clearGalleryWallpaper() {
        withContext(Dispatchers.IO) {
            appContext.filesDir.listFiles()
                ?.filter { it.name.startsWith("ciphertalk_wallpaper_") }
                ?.forEach { it.delete() }
        }
        appContext.uiPrefsDataStore.edit { prefs ->
            prefs.remove(keyWallpaperPath)
        }
    }
}
