package id.dn.fostah.dnlibrary.datasource.local

interface ISecureStorage {
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun remove(key: String)
    suspend fun clear()
}

/**
 * Storage for sensitive values (tokens, credentials):
 * - Android: Jetpack Preferences DataStore (construct with an Android `Context`)
 * - iOS: Keychain (no-arg constructor)
 */
expect class SecureStorage : ISecureStorage {
    override suspend fun putString(key: String, value: String)
    override suspend fun getString(key: String): String?
    override suspend fun remove(key: String)
    override suspend fun clear()
}
