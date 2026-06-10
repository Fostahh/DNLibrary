package id.dn.fostah.dnlibrary.datasource.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile


data class NetworkManagerConfig(
    val baseUrl: String,
    val apiKey: String
)
class NetworkManager private constructor(val config: NetworkManagerConfig) {

    companion object {
        @Volatile
        private var instance: NetworkManager? = null

        fun initialize(config: NetworkManagerConfig): NetworkManager {
            return instance ?: NetworkManager(config).also { instance = it }
        }

        fun getInstance(): NetworkManager {
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