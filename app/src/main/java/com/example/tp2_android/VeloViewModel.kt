package com.example.tp2_android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class VeloViewModel : ViewModel(){
    private val _stations = MutableStateFlow(listOf<V3Record>())
    private val _isRefreshing = MutableStateFlow(false)
    private val _messageStatus = MutableStateFlow("Chargement...")
    private val _searchQuery = MutableStateFlow("")
    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _sortByDistance = MutableStateFlow(false)
    val sortByDistance: StateFlow<Boolean> = _sortByDistance.asStateFlow()


    val stations: StateFlow<List<V3Record>> = _stations.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val messageStatus: StateFlow<String> = _messageStatus.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val api: VeloApi = Retrofit.Builder()
        .baseUrl("https://opendata.bordeaux-metropole.fr/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(VeloApi::class.java)

    init { loadData() }

    fun toggleSortByDistance() {
        _sortByDistance.value = !_sortByDistance.value
    }



    fun loadData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val response = api.getStations()
                _stations.value = response.records
                val heure = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                _messageStatus.value = "Mis a jour a $heure"
            } catch (e: Exception) {
                _messageStatus.value = "Erreur : ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    @SuppressLint("MissingPermission") // permission verifiee cote View
    fun fetchUserLocation(context: Context) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                _userLocation.value = location
                if (location != null) _sortByDistance.value = true
            }
    }

    fun distanceKm(station: V3Fields, userLat: Double, userLon: Double): Float {
        val geo = station.geo_point_2d ?: return Float.MAX_VALUE
        val result = FloatArray(1)
        Location.distanceBetween(userLat, userLon, geo[0], geo[1], result)
        return result[0] / 1000f // convertir en km
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}