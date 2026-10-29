package com.vipuljha.dupedigger.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            onboardingCompleted = this[KEY_ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            nearDuplicatesEnabled = this[KEY_NEAR_ENABLED] ?: defaults.nearDuplicatesEnabled,
            semanticEnabled = this[KEY_SEMANTIC_ENABLED] ?: defaults.semanticEnabled,
            nearHammingThreshold = (this[KEY_NEAR_HAMMING] ?: defaults.nearHammingThreshold)
                .coerceIn(AppSettings.HammingRange),
            semanticThreshold = (this[KEY_SEMANTIC_THRESHOLD] ?: defaults.semanticThreshold)
                .coerceIn(AppSettings.SemanticRange),
            minFileSizeBytes = (this[KEY_MIN_FILE_SIZE] ?: defaults.minFileSizeBytes).coerceAtLeast(0L),
            skipHiddenFiles = this[KEY_SKIP_HIDDEN] ?: defaults.skipHiddenFiles,
            modelDownloadWifiOnly = this[KEY_MODEL_WIFI_ONLY] ?: defaults.modelDownloadWifiOnly,
            aiModelId = this[KEY_AI_MODEL],
            aiBackend = EmbeddingBackend.entries.firstOrNull { it.name == this[KEY_AI_BACKEND] } ?: defaults.aiBackend,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[KEY_ONBOARDING_COMPLETED] = settings.onboardingCompleted
        this[KEY_NEAR_ENABLED] = settings.nearDuplicatesEnabled
        this[KEY_SEMANTIC_ENABLED] = settings.semanticEnabled
        this[KEY_NEAR_HAMMING] = settings.nearHammingThreshold.coerceIn(AppSettings.HammingRange)
        this[KEY_SEMANTIC_THRESHOLD] = settings.semanticThreshold.coerceIn(AppSettings.SemanticRange)
        this[KEY_MIN_FILE_SIZE] = settings.minFileSizeBytes.coerceAtLeast(0L)
        this[KEY_SKIP_HIDDEN] = settings.skipHiddenFiles
        this[KEY_MODEL_WIFI_ONLY] = settings.modelDownloadWifiOnly
        settings.aiModelId?.let { this[KEY_AI_MODEL] = it } ?: remove(KEY_AI_MODEL)
        this[KEY_AI_BACKEND] = settings.aiBackend.name
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_NEAR_ENABLED = booleanPreferencesKey("near_duplicates_enabled")
        val KEY_SEMANTIC_ENABLED = booleanPreferencesKey("semantic_enabled")
        val KEY_NEAR_HAMMING = intPreferencesKey("near_hamming_threshold")
        val KEY_SEMANTIC_THRESHOLD = floatPreferencesKey("semantic_threshold")
        val KEY_MIN_FILE_SIZE = longPreferencesKey("min_file_size_bytes")
        val KEY_SKIP_HIDDEN = booleanPreferencesKey("skip_hidden_files")
        val KEY_MODEL_WIFI_ONLY = booleanPreferencesKey("model_download_wifi_only")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model_id")
        val KEY_AI_BACKEND = stringPreferencesKey("ai_backend")
    }
}
