package com.vyora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.vyora.core.designsystem.theme.VyoraTheme
import com.vyora.core.di.AppModule
import com.vyora.core.navigation.AppNavHost
import com.vyora.core.permissions.PermissionUtils

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Handled contextually
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissionLauncher.launch(PermissionUtils.REQUIRED_LOCATION_PERMISSIONS)

        // Inject dependencies using AppModule
        val identityRepository = AppModule.provideIdentityRepository(applicationContext)
        val locationProvider = AppModule.provideLocationRepository(applicationContext)
        val transport = AppModule.provideTransport()
        val messageRepository = AppModule.provideMessageRepository(transport)

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
