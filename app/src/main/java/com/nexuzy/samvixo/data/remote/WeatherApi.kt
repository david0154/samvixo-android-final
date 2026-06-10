package com.nexuzy.samvixo.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherResponse(
    val current: CurrentWeather
)

data class CurrentWeather(
    val temperature_2m: Double,
    val wind_speed_10m: Double
)

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,wind_speed_10m"
    ): WeatherResponse
}
