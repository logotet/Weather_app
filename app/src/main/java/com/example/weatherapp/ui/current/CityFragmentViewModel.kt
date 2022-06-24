package com.example.weatherapp.ui.current

import androidx.databinding.Bindable
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.R
import com.example.weatherapp.data.Result
import com.example.weatherapp.data.remote.checkResult
import com.example.weatherapp.interactors.apicalls.GetCurrentCityWeather
import com.example.weatherapp.interactors.apicalls.GetCurrentCoordWeather
import com.example.weatherapp.interactors.apicalls.GetHourlyWeather
import com.example.weatherapp.interactors.localcalls.citynames.InsertCityName
import com.example.weatherapp.interactors.localcalls.hours.GetLocationHours
import com.example.weatherapp.interactors.localcalls.hours.InsertListOfHours
import com.example.weatherapp.interactors.localcalls.locations.*
import com.example.weatherapp.models.Measure
import com.example.weatherapp.models.current.Coord
import com.example.weatherapp.models.current.CurrentWeatherModel
import com.example.weatherapp.models.hourly.HourWeatherModel
import com.example.weatherapp.models.local.City
import com.example.weatherapp.models.local.LocalWeatherModel
import com.example.weatherapp.models.local.SavedLocation
import com.example.weatherapp.models.utils.mapApiToCurrentModel
import com.example.weatherapp.models.utils.mapLocalToCurrentModel
import com.example.weatherapp.models.utils.mapToCurrentHours
import com.example.weatherapp.models.utils.mapToLocalHours
import com.example.weatherapp.ui.ObservableViewModel
import com.example.weatherapp.ui.utils.onNetworkAvailability
import com.example.weatherapp.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CityFragmentViewModel @Inject constructor(
    private val getHourlyWeather: GetHourlyWeather,
    private val insertCityName: InsertCityName,
    private val resourceProvider: ResourceProvider,
    private val removeLocationFromFavorites: RemoveLocationFromFavorites,
    private val getLocationByName: GetLocationByName,
    private val insertListOfHours: InsertListOfHours,
    private val getCurrentCityWeather: GetCurrentCityWeather,
    private val getCurrentCoordWeather: GetCurrentCoordWeather,
    private val getLocationHours: GetLocationHours,
    private val getSavedLocationByCoords: GetSavedLocationByCoords,
    private val insertSavedLocation: InsertSavedLocation,
    private val getFavoriteLocationByName: GetFavoriteLocationByName
) : ObservableViewModel() {

    private var _locationName = MutableStateFlow<String?>(null)
    val locationName: StateFlow<String?> = _locationName

    private var _hours = MutableStateFlow<List<HourWeatherModel>?>(null)
    val hours: StateFlow<List<HourWeatherModel>?>
        get() = _hours

    private var _coords = MutableStateFlow<Coord?>(null)
    val coords: StateFlow<Coord?> = _coords

    private var weatherModel: CurrentWeatherModel? = null
        set(value) {
            field = value
            value?.let {
                _coords.value = Coord(value.lon, value.lat)
            }
            getNetworkHours()
            notifyChange()
        }

    var measure: Measure = Measure.METRIC

    private var isNetworkAvailable: Boolean = true

    private var latitude: Double? = null
    private var longitude: Double? = null

    private var _errorMessage = SingleLiveEvent<String?>()
    val errorMessage: SingleLiveEvent<String?>
        get() = _errorMessage

    @get:Bindable
    val cityName: String?
        get() = weatherModel?.name

    @get:Bindable
    val description: String?
        get() = weatherModel?.description

    @get:Bindable
    val temperature: String
        get() = weatherModel?.temperature.formatTemperature(resourceProvider, measure)

    @get:Bindable
    val humidity: String
        get() = weatherModel?.humidity.formatHumidity(resourceProvider)

    @get:Bindable
    val windSpeed: String
        get() = weatherModel?.windSpeed.formatSpeed(resourceProvider, measure)

    @get:Bindable
    val iconPath: String
        get() = AppConstants.IMG_URL + weatherModel?.icon + AppConstants.IMG_URL_SUFFIX

    @get:Bindable
    val rotation: Int?
        get() = weatherModel?.windDirection

    init {
        weatherModel?.let {
            getSavedLocation(it.name)
        }
    }

    fun setUpData(city: String?, lat: Double?, lon: Double?, measure: Measure) {
        this.measure = measure

        city?.let {
            if (city.isNotEmpty()) {
                getCurrentCityWeather(city)
                getSavedLocation(city)
            }
        }

        lat?.let {
            lon?.let {
                getCoordWeather(lat, lon)
                getSavedLocationByCoords()
            }
        }
    }

    fun getSavedLocation(city: String) {
        viewModelScope.launch {
            getLocationByName.getCity(city).collect { result ->
                checkLocalResult(result)
            }
        }
    }

    private fun getSavedLocationByCoords() {
        viewModelScope.launch {
            getSavedLocationByCoords.getCityByCoords().collect { result ->
                checkLocalResult(result)
            }
        }
    }

    private fun checkLocalResult(result: Result<LocalWeatherModel?>) {
        result.checkResult(
            {
                weatherModel = it?.mapLocalToCurrentModel()
                isSavedLocation(it?.name)
            },
            {
                return@checkResult
            }
        )
    }

    private fun getCurrentCityWeather(city: String?) {
        this.onNetworkAvailability(isNetworkAvailable,
            {
                viewModelScope.launch {
                    city?.let {
                        getCurrentCityWeather.getCurrentWeather(it, measure.value)
                            .collect { result ->
                                checkNetworkResult(result)
                            }
                    }
                }
                notifyChange()
            },
            {
                _errorMessage.value = resourceProvider.getString(R.string.no_network_message)
            })
    }

    private fun getCoordWeather(lat: Double?, lon: Double?) {
        this.onNetworkAvailability(
            isNetworkAvailable,
            {
                latitude = lat
                longitude = lon
                viewModelScope.launch {
                    latitude?.let { lat ->
                        longitude?.let { lon ->
                            getCurrentCoordWeather.getCurrentCoordWeather(lat,
                                lon,
                                measure.value).collect {
                                checkNetworkResult(it)
                            }
                        }
                    }
                    notifyChange()
                }
            },
            {
                _errorMessage.value = resourceProvider.getString(R.string.no_network_message)
            }
        )
    }

    private fun checkNetworkResult(result: Result<Unit>) {
        result.checkResult(
            {},
            { _errorMessage.value = it.message }
        )
    }

    private fun getNetworkHours() {
        viewModelScope.launch {
            weatherModel?.let { model ->
             getHourlyWeather.getHours(measure.value,
                    model.lat,
                    model.lon,
                    model.name).collect {result ->
                    result
                        .checkResult(
                        {
                            insertRecentCity()
                            getLocalHours(model.name)
                        },
                        {
                            _errorMessage.value = it.message
                        }
                    )
                }
            }
            notifyChange()
        }
    }

    private fun getLocalHours(locationName: String){
            viewModelScope.launch {
                getLocationHours.getLocationHours(locationName).collect {
                    _hours.emit(it.mapToCurrentHours())
                }
            }

    }

    fun isSavedLocation(name: String?){
        viewModelScope.launch {
            name?.let {
                getFavoriteLocationByName.getSavedLocation(it).collect {
                    _locationName.emit(it)
                }
            }
        }
    }

    fun saveWeatherData() {
        insertLocationAsSaved()
        insertLocationHoursAsSaved()
    }

    private fun insertLocationAsSaved() {
        viewModelScope.launch {
            weatherModel?.let {
                val localModel = it.mapApiToCurrentModel()
                insertSavedLocation.insertData(SavedLocation(localModel.name))
                getSavedLocation(it.name)
            }
        }
    }

    private fun insertLocationHoursAsSaved() {
        viewModelScope.launch {
            hours.value?.let {
                insertListOfHours.insertHours(it.mapToLocalHours(weatherModel!!.name))
            }
        }
    }

    fun removeLocationFromFavorites() {
        viewModelScope.launch {
            weatherModel?.let {
                removeLocationFromFavorites.removeFromFavorites(it.name)
                getSavedLocation(it.name)
            }
        }
    }

    private fun insertRecentCity() {
        viewModelScope.launch {
            weatherModel?.let {
                insertCityName.insertCityName(City(it.name))
            }
        }
    }
}
