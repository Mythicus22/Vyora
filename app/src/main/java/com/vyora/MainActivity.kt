package com.vyora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.vyora.core.designsystem.theme.VyoraTheme
import com.vyora.core.di.AppModule
import com.vyora.core.navigation.AppNavHost
import com.vyora.core.permissions.PermissionUtils
import kotlinx.coroutines.launch

import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Handled contextually
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize MapLibre Native SDK for OpenStreetMap rendering & offline caching
        MapLibre.getInstance(this)

        requestPermissionLauncher.launch(PermissionUtils.REQUIRED_LOCATION_PERMISSIONS)

        // Inject dependencies using AppModule
        val identityRepository = AppModule.provideIdentityRepository(applicationContext)
        val locationProvider = AppModule.provideLocationRepository(applicationContext)
        val transport = AppModule.provideTransport(applicationContext)
        val messageRepository = AppModule.provideMessageRepository(transport)

        lifecycleScope.launch {
            transport.start()
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
}
