package id.dn.fostah.dnlibrary.datasource.local

interface IPreferenceStorage {
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun remove(key: String)
    suspend fun clear()
}

/**
 * Storage for non-sensitive preferences and flags, e.g. whether the user
 * already finished onboarding:
 * - Android: Jetpack Preferences DataStore (construct with an Android `Context`)
 * - iOS: NSUserDefaults (no-arg constructor)
 *
 * Not substitutable with [SecureStorage]: values stored here are plaintext.
 */
expect class PreferenceStorage : IPreferenceStorage {
    override suspend fun putBoolean(key: String, value: Boolean)
    override suspend fun getBoolean(key: String, default: Boolean): Boolean
    override suspend fun putString(key: String, value: String)
    override suspend fun getString(key: String): String?
    override suspend fun remove(key: String)
    override suspend fun clear()
}
