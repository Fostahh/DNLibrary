package id.dn.fostah.dnlibrary.datasource.local

import platform.Foundation.NSUserDefaults

actual class PreferenceStorage : IPreferenceStorage {

    // Own suite keeps this library's entries separate from the host app's standardUserDefaults,
    // so clear() can never wipe the app's own settings
    private val suiteName = "id.dn.fostah.DNLibrary.preferences"
    private val userDefaults = NSUserDefaults(suiteName = suiteName)

    actual override suspend fun putBoolean(key: String, value: Boolean) {
        userDefaults.setBool(value, forKey = key)
    }

    actual override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        // boolForKey returns false for missing keys, so check existence to honor [default]
        return if (userDefaults.objectForKey(key) != null) userDefaults.boolForKey(key) else default
    }

    actual override suspend fun putString(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
    }

    actual override suspend fun getString(key: String): String? {
        return userDefaults.stringForKey(key)
    }

    actual override suspend fun remove(key: String) {
        userDefaults.removeObjectForKey(key)
    }

    actual override suspend fun clear() {
        userDefaults.removePersistentDomainForName(suiteName)
    }
}
