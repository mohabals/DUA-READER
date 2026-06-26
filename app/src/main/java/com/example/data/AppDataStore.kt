package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "experimental_settings")

class AppDataStore(private val context: Context) {
    companion object {
        val FEATURE_WORD_HIGHLIGHT = booleanPreferencesKey("feature_word_highlight")
        val FEATURE_GRAMMAR_COLOR = booleanPreferencesKey("feature_grammar_color")
        val FEATURE_KARAOKE_TTS = booleanPreferencesKey("feature_karaoke_tts")
        val FEATURE_PRONUNCIATION_COACH = booleanPreferencesKey("feature_pronunciation_coach")
    }

    val featureWordHighlightFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FEATURE_WORD_HIGHLIGHT] ?: false
    }

    val featureGrammarColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FEATURE_GRAMMAR_COLOR] ?: false
    }

    val featureKaraokeTtsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FEATURE_KARAOKE_TTS] ?: false
    }

    val featurePronunciationCoachFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FEATURE_PRONUNCIATION_COACH] ?: false
    }

    suspend fun setFeatureWordHighlight(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FEATURE_WORD_HIGHLIGHT] = enabled
        }
    }

    suspend fun setFeatureGrammarColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FEATURE_GRAMMAR_COLOR] = enabled
        }
    }

    suspend fun setFeatureKaraokeTts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FEATURE_KARAOKE_TTS] = enabled
        }
    }

    suspend fun setFeaturePronunciationCoach(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FEATURE_PRONUNCIATION_COACH] = enabled
        }
    }
}
