package id.dn.fostah.dnlibrary.datasource.local

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class SecureStorage : ISecureStorage {

    // Scopes every entry written by this library inside the host app's Keychain
    private val serviceName = "id.dn.fostah.DNLibrary"

    actual override suspend fun putString(key: String, value: String) {
        remove(key) // SecItemAdd rejects duplicate items, so replace any existing entry

        @Suppress("CAST_NEVER_SUCCEEDS")
        val valueData = CFBridgingRetain((value as NSString).dataUsingEncoding(NSUTF8StringEncoding))
        try {
            val status = keychainCall(key, kSecValueData to valueData) { query ->
                SecItemAdd(query, null)
            }
            if (status != errSecSuccess) {
                throw Exception("Failed to write '$key' to Keychain (OSStatus $status).")
            }
        } finally {
            CFBridgingRelease(valueData)
        }
    }

    actual override suspend fun getString(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = keychainCall(
            key,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        ) { query ->
            SecItemCopyMatching(query, result.ptr)
        }

        when (status) {
            errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.let { data ->
                NSString.create(data = data, encoding = NSUTF8StringEncoding).toString()
            }
            errSecItemNotFound -> null
            else -> throw Exception("Failed to read '$key' from Keychain (OSStatus $status).")
        }
    }

    actual override suspend fun remove(key: String) {
        val status = keychainCall(key) { query -> SecItemDelete(query) }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw Exception("Failed to remove '$key' from Keychain (OSStatus $status).")
        }
    }

    actual override suspend fun clear() {
        val status = keychainCall(account = null) { query -> SecItemDelete(query) }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw Exception("Failed to clear Keychain entries (OSStatus $status).")
        }
    }

    /**
     * Builds a generic-password Keychain query scoped to [serviceName] (and [account] when given),
     * runs [block] with it, then releases everything that was bridged to CoreFoundation.
     */
    private fun keychainCall(
        account: String?,
        vararg extras: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> Int
    ): Int {
        val serviceRef = CFBridgingRetain(serviceName)
        val accountRef = account?.let { CFBridgingRetain(it) }

        val attributes = buildMap<CFStringRef?, CFTypeRef?> {
            put(kSecClass, kSecClassGenericPassword)
            put(kSecAttrService, serviceRef)
            accountRef?.let { put(kSecAttrAccount, it) }
            putAll(extras)
        }

        val query = CFDictionaryCreateMutable(null, attributes.size.convert(), null, null)
        attributes.forEach { (k, v) -> CFDictionaryAddValue(query, k, v) }

        return try {
            block(query)
        } finally {
            CFRelease(query)
            CFBridgingRelease(serviceRef)
            accountRef?.let { CFBridgingRelease(it) }
        }
    }
}
