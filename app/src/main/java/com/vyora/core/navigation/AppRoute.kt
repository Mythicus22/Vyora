package com.vyora.core.navigation

sealed class AppRoute(val route: String) {
    object Onboarding : AppRoute("onboarding")
    object Map : AppRoute("map")
    object Assistant : AppRoute("assistant")
    object Messages : AppRoute("messages")
    object Chat : AppRoute("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/$peerId"
    }
    object Sos : AppRoute("sos")
    object Contacts : AppRoute("contacts")
    object EmergencyGuides : AppRoute("emergency_guides")
    object Settings : AppRoute("settings")
    object Diagnostics : AppRoute("diagnostics")
}
