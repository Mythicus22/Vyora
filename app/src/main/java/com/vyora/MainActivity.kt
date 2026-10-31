package com.vyora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.vyora.communication.MessageTransport
import com.vyora.communication.radio.UsbSerialTransport
import com.vyora.core.designsystem.theme.VyoraTheme
import com.vyora.core.di.AppModule
import com.vyora.core.location.LocationRepository
import com.vyora.core.navigation.AppNavHost
import com.vyora.core.permissions.PermissionUtils
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private lateinit var locationProvider: LocationRepository
    private lateinit var transport: MessageTransport

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            lifecycleScope.launch {
                locationProvider.startLocationUpdates()
                locationProvider.getCurrentFix()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize MapLibre Native SDK for OpenStreetMap rendering & offline caching
        MapLibre.getInstance(this)

        // Inject dependencies using AppModule
        val identityRepository = AppModule.provideIdentityRepository(applicationContext)
        locationProvider = AppModule.provideLocationRepository(applicationContext)
        transport = AppModule.provideTransport(applicationContext)
        val messageRepository = AppModule.provideMessageRepository(transport)

        requestPermissionLauncher.launch(PermissionUtils.REQUIRED_LOCATION_PERMISSIONS)

        lifecycleScope.launch {
            transport.start()
        }

        // Synchronize local identity with ESP32 node via {"cmd":"set_profile",...}
        lifecycleScope.launch {
            identityRepository.identityFlow.collect { identity ->
                if (identity != null && transport is UsbSerialTransport) {
                    (transport as UsbSerialTransport).setUserProfile(
                        userId = identity.userId,
                        callsign = identity.nickname
                    )
                }
            }
        }

        // Synchronize real phone GPS coordinates with ESP32 node via {"cmd":"set_location",...}
        lifecycleScope.launch {
            locationProvider.coordinates.collect { coords ->
                if (coords != null && transport is UsbSerialTransport) {
                    (transport as UsbSerialTransport).updateLocation(
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        enabled = true
                    )
                }
            }
        }

        setContent {
            VyoraTheme {
                AppNavHost(
                    identityRepository = identityRepository,
                    locationProvider = locationProvider,
                    transport = transport,
                    messageRepository = messageRepository
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::locationProvider.isInitialized && locationProvider.hasLocationPermission()) {
            locationProvider.startLocationUpdates()
            lifecycleScope.launch {
                locationProvider.getCurrentFix()
            }
        }
    }
}
