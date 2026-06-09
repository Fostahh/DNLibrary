package id.dn.fostah.dnlibrary

import id.dn.fostah.dnlibrary.network.DNNetworkManager
import id.dn.fostah.dnlibrary.network.responses.VideoGameResponse

class Greeting {
    private val platform = getPlatform()
    private val dnNetworkManager = DNNetworkManager()

    fun greet(): String {
        return sayHello(platform.name)
    }

    suspend fun dnApiCall(): List<VideoGameResponse> = dnNetworkManager.getDaFreakingVideoGames()
}