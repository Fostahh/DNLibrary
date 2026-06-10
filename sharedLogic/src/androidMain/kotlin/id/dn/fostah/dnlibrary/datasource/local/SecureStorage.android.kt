package id.dn.fostah.dnlibrary.datasource.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Top-level delegate so the process holds a single DataStore for this file, as DataStore requires
private val Context.dnSecureStorage by preferencesDataStore(name = "dn_library_secure_storage")

actual class SecureStorage(context: Context) : ISecureStorage {

    private val dataStore = context.applicationContext.dnSecureStorage

    actual override suspend fun putString(key: String, value: String) {
        try {
            dataStore.edit { preferences -> preferences[stringPreferencesKey(key)] = value }
        } catch (e: Exception) {
            throw Exception("Failed to write '$key' to secure storage: ${e.message}", e)
        }
    }

    actual override suspend fun getString(key: String): String? {
        try {
            return dataStore.data.first()[stringPreferencesKey(key)]
        } catch (e: Exception) {
            throw Exception("Failed to read '$key' from secure storage: ${e.message}", e)
        }
    }

    actual override suspend fun remove(key: String) {
        try {
            dataStore.edit { preferences -> preferences.remove(stringPreferencesKey(key)) }
        } catch (e: Exception) {
            throw Exception("Failed to remove '$key' from secure storage: ${e.message}", e)
        }
    }

    actual override suspend fun clear() {
        try {
            dataStore.edit { preferences -> preferences.clear() }
        } catch (e: Exception) {
            throw Exception("Failed to clear secure storage: ${e.message}", e)
        }
    }
}
