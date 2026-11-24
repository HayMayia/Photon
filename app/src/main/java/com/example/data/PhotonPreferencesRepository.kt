package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.photonDataStore: DataStore<Preferences> by preferencesDataStore(name = "photon_yt_prefs")

data class PersistedLocalPrefs(
    val recentSearches: List<String> = emptyList(),
    val defaultMode: AppMode = AppMode.YOUTUBE,
    val autoplayEnabled: Boolean = true,
    val youtubeSessionCookie: String = ""
)

class PhotonPreferencesRepository(private val context: Context) {

    private object Keys {
        val RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val AUTOPLAY = booleanPreferencesKey("autoplay_enabled")
        val YT_SESSION_COOKIE = stringPreferencesKey("yt_session_cookie_private")
    }

    val state: Flow<PersistedLocalPrefs> = context.photonDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val mode = prefs[Keys.DEFAULT_MODE]?.let { name ->
                AppMode.entries.find { it.name == name }
            } ?: AppMode.YOUTUBE

            PersistedLocalPrefs(
                recentSearches = decodeStringList(prefs[Keys.RECENT_SEARCHES_JSON].orEmpty()),
                defaultMode = mode,
                autoplayEnabled = prefs[Keys.AUTOPLAY] ?: true,
                youtubeSessionCookie = prefs[Keys.YT_SESSION_COOKIE].orEmpty()
            )
        }

    suspend fun saveRecentSearches(searches: List<String>) {
        context.photonDataStore.edit { it[Keys.RECENT_SEARCHES_JSON] = encodeStringList(searches.take(10)) }
    }

    suspend fun setDefaultMode(mode: AppMode) {
        context.photonDataStore.edit { it[Keys.DEFAULT_MODE] = mode.name }
    }

    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.AUTOPLAY] = enabled }
    }

    suspend fun saveYouTubeSessionCookie(cookie: String) {
        context.photonDataStore.edit { it[Keys.YT_SESSION_COOKIE] = cookie }
    }

    suspend fun clearSessionOnSignOut() {
        context.photonDataStore.edit {
            it.remove(Keys.YT_SESSION_COOKIE)
        }
    }

    private fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) add(s)
                }
            }
        }.getOrDefault(emptyList())
    }
}
