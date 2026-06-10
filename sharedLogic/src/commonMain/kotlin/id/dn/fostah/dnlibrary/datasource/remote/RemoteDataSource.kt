package id.dn.fostah.dnlibrary.datasource.remote

import id.dn.fostah.dnlibrary.datasource.remote.network.DNNetworkManager
import id.dn.fostah.dnlibrary.datasource.remote.network.responses.ListVideoGameResponse
import id.dn.fostah.dnlibrary.datasource.remote.network.responses.VideoGameResponse
import io.ktor.client.call.body
import io.ktor.client.request.get

interface IRemoteDataSource {
    suspend fun getDaFreakingVideoGames(): List<VideoGameResponse>
}
class RemoteDataSource(private val networkManager: DNNetworkManager): IRemoteDataSource {
    override suspend fun getDaFreakingVideoGames(): List<VideoGameResponse> {
        try {
            val response: ListVideoGameResponse = networkManager.httpClient.get(
                networkManager.config.baseUrl+"?key=${networkManager.config.apiKey}"
            ).body()
            return response.results
        } catch (e: Exception) {
            throw Exception("Failed to fetch video games: ${e.message}", e)
        }
    }
}