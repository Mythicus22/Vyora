package com.vyora.core.permissions

import android.Manifest

object PermissionUtils {
    val REQUIRED_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}
