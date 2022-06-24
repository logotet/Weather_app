package com.example.weatherapp.interactors.apicalls

import com.example.weatherapp.data.Result
import com.example.weatherapp.repository.Repository
import kotlinx.coroutines.flow.Flow

class GetHourlyWeather(
    private val repository: Repository,
) {
    suspend fun getHours(
        measure: String,
        lat: Double,
        lon: Double,
        city: String
    ): Flow<Result<Unit>> {
        return repository.getHourlyWeather(measure, lat, lon, city)
    }
}