package com.nexuzy.samvixo.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GiphyApi {
    @GET("v1/gifs/search")
    suspend fun searchGifs(
        @Query("api_key") apiKey: String,
        @Query("q")       query: String,
        @Query("limit")   limit: Int = 20,
        @Query("rating")  rating: String = "g"
    ): GiphySearchResponse

    @GET("v1/gifs/trending")
    suspend fun trendingGifs(
        @Query("api_key") apiKey: String,
        @Query("limit")   limit: Int = 20,
        @Query("rating")  rating: String = "g"
    ): GiphySearchResponse
}

data class GiphySearchResponse(
    val data: List<GiphyGif> = emptyList()
)

data class GiphyGif(
    val id: String = "",
    val title: String = "",
    val images: GiphyImages = GiphyImages()
)

data class GiphyImages(
    val fixed_height: GiphyImageData = GiphyImageData(),
    val fixed_height_small: GiphyImageData = GiphyImageData(),
    val original: GiphyImageData = GiphyImageData()
)

data class GiphyImageData(
    val url: String = "",
    val mp4: String = "",
    val width: String = "0",
    val height: String = "0"
)
