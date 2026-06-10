package id.dn.fostah.dnlibrary.datasource.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile


data class DNNetworkManagerConfig(
    val baseUrl: String,
    val apiKey: String
)
class DNNetworkManager private constructor(val config: DNNetworkManagerConfig) {

    companion object {
        @Volatile
        private var instance: DNNetworkManager? = null

        fun initialize(config: DNNetworkManagerConfig): DNNetworkManager {
            return instance ?: DNNetworkManager(config).also { instance = it }
        }

        fun getInstance(): DNNetworkManager {
            return instance ?: error("NetworkManager not initialized.")
        }
    }

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

}