package id.dn.fostah.dnlibrary.datasource.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Top-level delegate so the process holds a single DataStore for this file, as DataStore requires
private val Context.dnPreferenceStorage by preferencesDataStore(name = "dn_library_preferences")

actual class PreferenceStorage(context: Context) : IPreferenceStorage {

    private val dataStore = context.applicationContext.dnPreferenceStorage

    actual override suspend fun putBoolean(key: String, value: Boolean) {
        try {
            dataStore.edit { preferences -> preferences[booleanPreferencesKey(key)] = value }
        } catch (e: Exception) {
            throw Exception("Failed to write '$key' to preferences: ${e.message}", e)
        }
    }

    actual override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        try {
            return dataStore.data.first()[booleanPreferencesKey(key)] ?: default
        } catch (e: Exception) {
            throw Exception("Failed to read '$key' from preferences: ${e.message}", e)
        }
    }

    actual override suspend fun putString(key: String, value: String) {
        try {
            dataStore.edit { preferences -> preferences[stringPreferencesKey(key)] = value }
        } catch (e: Exception) {
            throw Exception("Failed to write '$key' to preferences: ${e.message}", e)
        }
    }

    actual override suspend fun getString(key: String): String? {
        try {
            return dataStore.data.first()[stringPreferencesKey(key)]
        } catch (e: Exception) {
            throw Exception("Failed to read '$key' from preferences: ${e.message}", e)
        }
    }

    actual override suspend fun remove(key: String) {
        try {
            // Preferences.Key equality is name-based, so this removes the value
            // under [key] regardless of its stored type
            dataStore.edit { preferences -> preferences.remove(stringPreferencesKey(key)) }
        } catch (e: Exception) {
            throw Exception("Failed to remove '$key' from preferences: ${e.message}", e)
        }
    }

    actual override suspend fun clear() {
        try {
            dataStore.edit { preferences -> preferences.clear() }
        } catch (e: Exception) {
            throw Exception("Failed to clear preferences: ${e.message}", e)
        }
    }
}
