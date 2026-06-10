package id.dn.fostah.dnlibrary.datasource.remote.network.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListVideoGameResponse(
    @SerialName("results")
    val results: List<VideoGameResponse>
)

@Serializable
data class VideoGameResponse(
    @SerialName("id")
    val id: Int? = null,

    @SerialName("name")
    val name: String? = null,

    @SerialName("background_image")
    val backgroundImage: String? = null,

    @SerialName("released")
    val released: String? = null,

    @SerialName("rating")
    val rating: Double? = null,
)
