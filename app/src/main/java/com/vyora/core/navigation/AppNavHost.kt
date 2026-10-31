package com.vyora.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.vyora.ai.EmergencyKnowledgeRepository
import com.vyora.communication.MessageTransport
import com.vyora.core.designsystem.theme.VyoraNavyDark
import com.vyora.core.location.LocationProvider
import com.vyora.data.assistant.AssistantRepositoryImpl
import com.vyora.data.devices.DeviceRepositoryImpl
import com.vyora.domain.model.UserIdentity
import com.vyora.domain.repository.IdentityRepository
import com.vyora.domain.repository.MessageRepository
import com.vyora.feature.assistant.AssistantScreen
import com.vyora.feature.assistant.AssistantViewModel
import com.vyora.feature.contacts.ContactsScreen
import com.vyora.feature.diagnostics.DiagnosticsScreen
import com.vyora.feature.emergencyguides.EmergencyGuidesScreen
import com.vyora.feature.map.MapScreen
import com.vyora.feature.map.MapViewModel
import com.vyora.feature.messaging.ChatScreen
import com.vyora.feature.messaging.MessagesScreen
import com.vyora.feature.messaging.MessagingViewModel
import com.vyora.feature.onboarding.OnboardingScreen
import com.vyora.feature.onboarding.OnboardingViewModel
import com.vyora.feature.settings.SettingsScreen
import com.vyora.feature.sos.SosScreen
import com.vyora.feature.sos.SosViewModel

@Composable
fun AppNavHost(
    identityRepository: IdentityRepository,
    locationProvider: LocationProvider,
    transport: MessageTransport,
    messageRepository: MessageRepository
) {
    val navController = rememberNavController()
    val identity by identityRepository.identityFlow.collectAsState(initial = null)
    val isOnboardingDone by identityRepository.isOnboardingCompleted.collectAsState(initial = false)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showSosModal by remember { mutableStateOf(false) }

    val showBottomBar = currentRoute in listOf(
        AppRoute.Map.route,
        AppRoute.Assistant.route,
        AppRoute.Messages.route
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    // ViewModels
    val onboardingViewModel = remember { OnboardingViewModel(identityRepository) }
    val deviceRepo = remember { DeviceRepositoryImpl(transport) }
    val offlineMapTileManager = remember { com.vyora.core.location.OfflineMapTileManager(context) }
    val mapViewModel = remember { MapViewModel(locationProvider, deviceRepo, offlineMapTileManager) }
    val assistantRepo = remember { AssistantRepositoryImpl(EmergencyKnowledgeRepository()) }
    val assistantViewModel = remember { AssistantViewModel(assistantRepo) }
    val messagingViewModel = remember { MessagingViewModel(messageRepository) }
    val sosViewModel = remember { SosViewModel(messageRepository) }

    val coordinates by locationProvider.coordinates.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(VyoraNavyDark)) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    VyoraBottomNavigation(
                        currentRoute = currentRoute,
                        onNavigateToRoute = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            },
            containerColor = VyoraNavyDark
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (isOnboardingDone) AppRoute.Map.route else AppRoute.Onboarding.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(AppRoute.Onboarding.route) {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onNavigateToHome = {
                            navController.navigate(AppRoute.Map.route) {
                                popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(AppRoute.Map.route) {
                    MapScreen(
                        viewModel = mapViewModel,
                        onStartChat = { peerId ->
                            navController.navigate(AppRoute.Chat.createRoute(peerId))
                        },
                        onTriggerSos = { showSosModal = true }
                    )
                }

                composable(AppRoute.Assistant.route) {
                    AssistantScreen(
                        viewModel = assistantViewModel,
                        onTriggerSos = { showSosModal = true }
                    )
                }

                composable(AppRoute.Messages.route) {
                    MessagesScreen(
                        viewModel = messagingViewModel,
                        onOpenChat = { peerId ->
                            navController.navigate(AppRoute.Chat.createRoute(peerId))
                        }
                    )
                }

                composable(
                    route = AppRoute.Chat.route,
                    arguments = listOf(navArgument("peerId") { type = NavType.StringType })
                ) { backStack ->
                    val peerId = backStack.arguments?.getString("peerId") ?: ""
                    ChatScreen(
                        peerId = peerId,
                        myUserId = identity?.userId ?: "UNKNOWN",
                        viewModel = messagingViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppRoute.Contacts.route) { ContactsScreen() }
                composable(AppRoute.EmergencyGuides.route) { EmergencyGuidesScreen() }
                composable(AppRoute.Settings.route) { SettingsScreen() }
                composable(AppRoute.Diagnostics.route) { DiagnosticsScreen(transport = transport) }
            }
        }

        if (showSosModal) {
            SosScreen(
                myUserId = identity?.userId ?: "VYO-LOCAL-ME",
                myNickname = identity?.nickname ?: "Local Operator",
                latitude = coordinates?.latitude,
                longitude = coordinates?.longitude,
                viewModel = sosViewModel,
                onDismiss = { showSosModal = false }
            )
        }
    }
}
