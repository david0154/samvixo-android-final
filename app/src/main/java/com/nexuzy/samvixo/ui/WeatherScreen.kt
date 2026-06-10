package com.nexuzy.samvixo.ui

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Locale

private data class WeatherData(
    val city         : String,
    val tempC         : Double,
    val feelsLike     : Double,
    val humidity      : Int,
    val windKph       : Double,
    val conditionText : String,
    val conditionEmoji: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(navController: NavController) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()

    var weather      by remember { mutableStateOf<WeatherData?>(null) }
    var isLoading    by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    var manualCity   by remember { mutableStateOf("") }
    var locationCity by remember { mutableStateOf<String?>(null) }
    var locDenied    by remember { mutableStateOf(false) }

    // ── Helpers ────────────────────────────────────────────────────────
    suspend fun fetchByLatLng(lat: Double, lng: Double, city: String) = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lng" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weathercode"
        val json   = JSONObject(URL(url).readText())
        val cur    = json.getJSONObject("current")
        val code   = cur.getInt("weathercode")
        val emoji  = weatherCodeToEmoji(code)
        val cond   = weatherCodeToText(code)
        WeatherData(
            city          = city,
            tempC         = cur.getDouble("temperature_2m"),
            feelsLike     = cur.getDouble("apparent_temperature"),
            humidity      = cur.getInt("relative_humidity_2m"),
            windKph       = cur.getDouble("wind_speed_10m"),
            conditionText = cond,
            conditionEmoji= emoji
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchByLocation() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val client   = LocationServices.getFusedLocationProviderClient(context)
                val location : Location? = client.lastLocation.await()
                if (location != null) {
                    val lat  = location.latitude
                    val lng  = location.longitude
                    val geo  = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addr = withContext(Dispatchers.IO) { geo.getFromLocation(lat, lng, 1) }
                    val city = addr?.firstOrNull()?.locality
                        ?: addr?.firstOrNull()?.adminArea
                        ?: "Your Location"
                    locationCity = city
                    weather = fetchByLatLng(lat, lng, city)
                } else {
                    error = "Unable to get current location. Try entering a city manually."
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to fetch weather"
            }
            isLoading = false
        }
    }

    fun fetchByCity(city: String) {
        scope.launch {
            isLoading = true
            error = null
            try {
                val geo = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addr = withContext(Dispatchers.IO) { geo.getFromLocationName(city, 1) }
                val a = addr?.firstOrNull()
                if (a != null) {
                    weather = fetchByLatLng(a.latitude, a.longitude, city)
                } else {
                    error = "City \"$city\" not found. Please check the name."
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to fetch weather"
            }
            isLoading = false
        }
    }

    // ── Location permission launcher ──────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchByLocation()
        else locDenied = true
    }

    // Auto-fetch on launch
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Manual city search bar
            OutlinedTextField(
                value         = manualCity,
                onValueChange = { manualCity = it },
                label         = { Text("Enter city name") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    IconButton(onClick = { if (manualCity.isNotBlank()) fetchByCity(manualCity.trim()) }) {
                        Icon(Icons.Default.Send, null)
                    }
                },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (manualCity.isNotBlank()) fetchByCity(manualCity.trim()) })
            )

            if (locDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "📍 Location permission denied. Enter a city above.",
                    fontSize = 12.sp, color = Color.Gray
                )
            }

            Spacer(Modifier.height(8.dp))

            // Use my location button
            OutlinedButton(
                onClick  = { fetchByLocation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MyLocation, null)
                Spacer(Modifier.width(8.dp))
                Text("Use My Location")
            }

            Spacer(Modifier.height(24.dp))

            when {
                isLoading -> CircularProgressIndicator()
                error != null -> {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
                weather != null -> WeatherCard(weather!!)
                else -> {
                    Text("☁️", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Tap \"Use My Location\" or search a city", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(w: WeatherData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(w.conditionEmoji, fontSize = 72.sp)
            Spacer(Modifier.height(8.dp))
            Text(w.city, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(w.conditionText, fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(16.dp))
            Text(
                "${w.tempC.toInt()}°C",
                fontSize = 56.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text("Feels like ${w.feelsLike.toInt()}°C", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherStat("💧", "Humidity", "${w.humidity}%")
                WeatherStat("💨", "Wind",     "${w.windKph.toInt()} km/h")
            }
        }
    }
}

@Composable
private fun WeatherStat(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji,  fontSize = 22.sp)
        Text(value,  fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label,  fontSize = 12.sp, color = Color.Gray)
    }
}

private fun weatherCodeToEmoji(code: Int) = when (code) {
    0            -> "☀️"
    1, 2         -> "🌤️"
    3            -> "☁️"
    45, 48       -> "🌫️"
    51, 53, 55   -> "🌦️"
    61, 63, 65   -> "🌧️"
    71, 73, 75   -> "❄️"
    80, 81, 82   -> "🌦️"
    95           -> "⛈️"
    96, 99       -> "⛈️"
    else         -> "🌡️"
}

private fun weatherCodeToText(code: Int) = when (code) {
    0            -> "Clear sky"
    1, 2         -> "Partly cloudy"
    3            -> "Overcast"
    45, 48       -> "Foggy"
    51, 53, 55   -> "Drizzle"
    61, 63, 65   -> "Rain"
    71, 73, 75   -> "Snow"
    80, 81, 82   -> "Rain showers"
    95           -> "Thunderstorm"
    96, 99       -> "Thunderstorm with hail"
    else         -> "Unknown"
}
