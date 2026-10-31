package com.vyora.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.vyora.BuildConfig
import com.vyora.core.designsystem.theme.*
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.model.ReachabilityStatus
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onStartChat: (String) -> Unit,
    onTriggerSos: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showDirectIdDialog by remember { mutableStateOf(false) }
    var showLiveDevicesSheet by remember { mutableStateOf(false) }
    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    val activeStyleUrl = remember(uiState.isSatelliteView) {
        if (uiState.isSatelliteView) {
            "https://tiles.openfreemap.org/styles/bright"
        } else {
            BuildConfig.MAP_STYLE_URL.ifEmpty { "https://tiles.openfreemap.org/styles/dark" }
        }
    }

    // Dynamically apply style when user toggles dark / street vector map layers
    LaunchedEffect(activeStyleUrl, mapLibreInstance) {
        val map = mapLibreInstance
        if (map != null && isMapLoaded) {
            map.setStyle(Style.Builder().fromUri(activeStyleUrl)) {
                refreshMapMarkers(map, uiState, onDeviceClick = { dev ->
                    viewModel.selectDevice(dev)
                })
            }
        }
    }

    // Keep camera positioned when coordinates update
    LaunchedEffect(uiState.myCoordinates, mapLibreInstance) {
        val coords = uiState.myCoordinates
        val map = mapLibreInstance
        if (coords != null && map != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(coords.latitude, coords.longitude),
                    14.5
                ),
                1000
            )
        }
    }

    // Reactively refresh map markers whenever mesh devices update or map finishes loading
    LaunchedEffect(uiState.filteredDevices, uiState.myCoordinates, isMapLoaded, mapLibreInstance) {
        val map = mapLibreInstance
        if (map != null && isMapLoaded) {
            refreshMapMarkers(map, uiState, onDeviceClick = { dev ->
                viewModel.selectDevice(dev)
            })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
    ) {
        // 1. MapLibre Native SDK with OpenStreetMap Vector Tiles & Offline Cache
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onCreate(null)

                    // Bind MapView lifecycle to Compose lifecycle
                    val observer = object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) = onStart()
                        override fun onResume(owner: LifecycleOwner) = onResume()
                        override fun onPause(owner: LifecycleOwner) = onPause()
                        override fun onStop(owner: LifecycleOwner) = onStop()
                        override fun onDestroy(owner: LifecycleOwner) = onDestroy()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)

                    getMapAsync { map ->
                        mapLibreInstance = map
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        val coords = uiState.myCoordinates
                        val initialLat = coords?.latitude ?: 0.0
                        val initialLng = coords?.longitude ?: 0.0
                        val initialZoom = if (coords != null) 14.5 else 1.0
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(initialLat, initialLng))
                            .zoom(initialZoom)
                            .build()

                        map.setStyle(Style.Builder().fromUri(activeStyleUrl)) {
                            isMapLoaded = true
                            refreshMapMarkers(map, uiState, onDeviceClick = { dev ->
                                viewModel.selectDevice(dev)
                            })
                        }

                        map.setOnMarkerClickListener { marker ->
                            val clicked = uiState.nearbyDevices.find {
                                marker.title?.startsWith(it.nickname) == true ||
                                marker.snippet?.contains(it.deviceId) == true ||
                                it.nickname == marker.title
                            }
                            if (clicked != null) {
                                viewModel.selectDevice(clicked)
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            },
            update = { mapView ->
                mapLibreInstance?.let { map ->
                    if (isMapLoaded) {
                        refreshMapMarkers(map, uiState, onDeviceClick = { dev ->
                            viewModel.selectDevice(dev)
                        })
                    }
                }
            }
        )

        // 2. Tactical MGRS Radar / Range Ring Canvas Overlay
        if (uiState.isRadarOverlayVisible && !uiState.isSatelliteView) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp)
            ) {
                val center = Offset(size.width / 2f, size.height * 0.46f)
                val maxRadius = size.width * 0.44f

                // Radar Range Rings (0.5km, 1.5km, 3.0km)
                listOf(0.35f, 0.68f, 1.0f).forEach { ratio ->
                    drawCircle(
                        color = VyoraCyanSecondary.copy(alpha = 0.16f),
                        radius = maxRadius * ratio,
                        center = center,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )
                }

                // Coordinate Reticle Lines
                drawLine(
                    color = VyoraCyanSecondary.copy(alpha = 0.20f),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
                drawLine(
                    color = VyoraCyanSecondary.copy(alpha = 0.20f),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            }
        }

        // 3. Top HUD: Mesh Health & Telemetry Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Offline Tile Cache Progress Notification
            AnimatedVisibility(visible = uiState.offlineProgress.isDownloading || uiState.offlineProgress.isComplete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VyoraNavyDark.copy(alpha = 0.95f))
                        .border(1.dp, if (uiState.offlineProgress.isComplete) VyoraMeshGreen else VyoraCyanSecondary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.offlineProgress.isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VyoraCyanSecondary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = VyoraMeshGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = uiState.offlineProgress.statusText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (uiState.offlineProgress.isComplete) VyoraMeshGreen else VyoraCyanSecondary
                    )
                }
            }

            // Main Telemetry Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VyoraNavyCard.copy(alpha = 0.94f))
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showLiveDevicesSheet = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (uiState.nearbyDevices.isNotEmpty()) VyoraMeshGreen else VyoraCyanSecondary)
                    )
                    Column {
                        Text(
                            text = "OPENSTREETMAP TACTICAL RADAR",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VyoraMeshGreen,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${uiState.nearbyDevices.size} NODES ACTIVE • Tap to view",
                            fontSize = 10.sp,
                            color = VyoraCyanLight
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Cache Nearby Offline Tiles Button
                    IconButton(
                        onClick = { viewModel.cacheCurrentRegion(activeStyleUrl) },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VyoraNavyHighlight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Cache Offline Tiles",
                            tint = VyoraMeshGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Direct ID Lookup Button
                    IconButton(
                        onClick = { showDirectIdDialog = true },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VyoraNavyHighlight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Address Node ID",
                            tint = VyoraCyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Technical Grid Coordinate Readout Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VyoraNavyContainer.copy(alpha = 0.90f))
                    .border(0.5.dp, VyoraNavyBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = VyoraCyanSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = uiState.myCoordinates?.let {
                            val latDir = if (it.latitude >= 0) "N" else "S"
                            val lngDir = if (it.longitude >= 0) "E" else "W"
                            String.format(java.util.Locale.US, "GRID: %.4f°%s, %.4f°%s", Math.abs(it.latitude), latDir, Math.abs(it.longitude), lngDir)
                        } ?: "GPS ACQUIRING FIX...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraCyanSecondary
                    )
                }

                Text(
                    text = "RADAR: 4.8 km • ACC: ±${uiState.myCoordinates?.accuracy?.toInt() ?: 12}m",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = VyoraTextSecondary
                )
            }

            // Quick Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapFilterCategory.values().forEach { category ->
                    val isSelected = uiState.activeFilter == category
                    val count = when (category) {
                        MapFilterCategory.ALL -> uiState.nearbyDevices.size
                        MapFilterCategory.SOS -> uiState.nearbyDevices.count { it.nickname.contains("SOS", true) }
                        MapFilterCategory.MEDICAL -> uiState.nearbyDevices.count { it.nickname.contains("Medic", true) }
                        MapFilterCategory.RELAY -> uiState.nearbyDevices.count { it.nickname.contains("Relay", true) }
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilter(category) },
                        label = {
                            Text(
                                text = "${category.label} ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = VyoraNavyCard,
                            labelColor = VyoraTextSecondary,
                            selectedContainerColor = if (category == MapFilterCategory.SOS) VyoraEmergencyOrange else VyoraCyanSecondary,
                            selectedLabelColor = VyoraNavyDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = VyoraNavyBorder,
                            selectedBorderColor = VyoraCyanSecondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // 4. Tactical Control Buttons (Right Side Vertical Rail)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Layer Toggle (Dark Tactical Map / Bright Street Map)
            IconButton(
                onClick = {
                    viewModel.toggleSatelliteView()
                    val willBeBright = !uiState.isSatelliteView
                    android.widget.Toast.makeText(
                        context,
                        if (willBeBright) "Switched to Bright Street Map" else "Switched to Tactical Dark Map",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, if (uiState.isSatelliteView) VyoraCyanSecondary else VyoraNavyBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Toggle Map Style",
                    tint = if (uiState.isSatelliteView) VyoraCyanSecondary else VyoraTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Radar Ring Overlay Toggle
            IconButton(
                onClick = {
                    viewModel.toggleRadarOverlay()
                    val willBeVisible = !uiState.isRadarOverlayVisible
                    android.widget.Toast.makeText(
                        context,
                        if (willBeVisible) "Radar Range Rings: ON" else "Radar Range Rings: OFF",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, if (uiState.isRadarOverlayVisible) VyoraMeshGreen else VyoraNavyBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = "Toggle Radar Grid",
                    tint = if (uiState.isRadarOverlayVisible) VyoraMeshGreen else VyoraTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom In Button
            IconButton(
                onClick = {
                    mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomIn(), 300)
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = VyoraCyanSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom Out Button
            IconButton(
                onClick = {
                    mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomOut(), 300)
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = VyoraCyanSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Offline Cache Region Trigger
            IconButton(
                onClick = {
                    val targetLat = uiState.myCoordinates?.latitude ?: mapLibreInstance?.cameraPosition?.target?.latitude
                    val targetLng = uiState.myCoordinates?.longitude ?: mapLibreInstance?.cameraPosition?.target?.longitude
                    if (targetLat != null && targetLng != null && (targetLat != 0.0 || targetLng != 0.0)) {
                        viewModel.cacheCurrentRegion(activeStyleUrl, targetLat, targetLng)
                        android.widget.Toast.makeText(context, "Caching offline tiles for this region...", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Acquiring GPS location or map center to cache...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Cache Region",
                    tint = VyoraCyanSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Recenter GPS Button
            IconButton(
                onClick = {
                    viewModel.recenterGps()
                    val coords = uiState.myCoordinates
                    if (coords != null) {
                        mapLibreInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(coords.latitude, coords.longitude),
                                15.0
                            ),
                            800
                        )
                        android.widget.Toast.makeText(
                            context,
                            String.format(java.util.Locale.US, "Centered on GPS (%.4f, %.4f)", coords.latitude, coords.longitude),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(context, "Acquiring real-time GPS fix...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter GPS",
                    tint = VyoraCyanSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Emergency SOS Action Button
            IconButton(
                onClick = onTriggerSos,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VyoraEmergencyOrange)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Trigger SOS",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 5. Selected Node Detail Bottom Card
        AnimatedVisibility(
            visible = uiState.selectedDevice != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            uiState.selectedDevice?.let { device ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VyoraNavyCard)
                        .border(1.dp, VyoraCyanSecondary, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = device.nickname,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = VyoraTextPrimary
                            )
                            Text(
                                text = "NODE ID: ${device.deviceId}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = VyoraCyanLight
                            )
                        }

                        IconButton(onClick = { viewModel.selectDevice(null) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = VyoraTextSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("LINK STATUS", fontSize = 10.sp, color = VyoraTextSecondary, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (device.reachability == ReachabilityStatus.REACHABLE_DIRECT) "DIRECT (1 HOP)" else "MESH RELAY (${device.hopCount} HOPS)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (device.reachability == ReachabilityStatus.REACHABLE_DIRECT) VyoraMeshGreen else VyoraCyanSecondary
                            )
                        }
                        Column {
                            Text("SIGNAL (RSSI)", fontSize = 10.sp, color = VyoraTextSecondary, fontFamily = FontFamily.Monospace)
                            Text("${device.rssi} dBm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                        }
                        Column {
                            Text("RADIO BAND", fontSize = 10.sp, color = VyoraTextSecondary, fontFamily = FontFamily.Monospace)
                            Text("915 MHz ISM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                        }
                    }

                    Button(
                        onClick = { onStartChat(device.deviceId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VyoraCyanSecondary,
                            contentColor = VyoraNavyDark
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("OPEN PRIVATE MESH CHAT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 6. Direct Node ID Lookup Dialog
        if (showDirectIdDialog) {
            var directInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showDirectIdDialog = false },
                containerColor = VyoraNavyCard,
                title = { Text("Direct Node Addressing", color = VyoraTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Enter any peer unique ID (e.g. VYO-XXXX-XXXX). Packets queue locally and route when radio connectivity or relays are found.",
                            fontSize = 12.sp,
                            color = VyoraTextSecondary
                        )
                        OutlinedTextField(
                            value = directInput,
                            onValueChange = { directInput = it.uppercase() },
                            placeholder = { Text("VYO-XXXX-XXXX") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean = directInput.trim()
                            if (clean.isNotEmpty()) {
                                showDirectIdDialog = false
                                onStartChat(clean)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VyoraCyanSecondary,
                            contentColor = VyoraNavyDark
                        )
                    ) {
                        Text("Open Conversation")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDirectIdDialog = false }) {
                        Text("Cancel", color = VyoraTextSecondary)
                    }
                }
            )
        }

        // 7. Live Mesh Nodes Sheet
        if (showLiveDevicesSheet) {
            AlertDialog(
                onDismissRequest = { showLiveDevicesSheet = false },
                containerColor = VyoraNavyCard,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE MESH NODES (${uiState.nearbyDevices.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = VyoraTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (uiState.nearbyDevices.isNotEmpty()) VyoraMeshGreen.copy(alpha = 0.2f) else VyoraEmergencyOrange.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (uiState.nearbyDevices.isNotEmpty()) "RF ONLINE" else "LISTENING",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.nearbyDevices.isNotEmpty()) VyoraMeshGreen else VyoraEmergencyOrange
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.nearbyDevices.isEmpty()) {
                            Text(
                                text = "No live mesh peers detected yet.\nEnsure other ESP32 nodes are powered on with RF24 transceivers within 2.4GHz range (Channel 76).",
                                fontSize = 12.sp,
                                color = VyoraTextSecondary
                            )
                        } else {
                            uiState.nearbyDevices.forEach { dev ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = VyoraNavyHighlight),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(dev.nickname, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VyoraTextPrimary)
                                                Text("ID: ${dev.deviceId}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = VyoraCyanLight)
                                            }
                                            Surface(
                                                color = VyoraNavyDark,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${dev.hopCount} hop • ${dev.rssi}dBm",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = VyoraMeshGreen
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.selectDevice(dev)
                                                    showLiveDevicesSheet = false
                                                    val lat = dev.latitude
                                                    val lng = dev.longitude
                                                    if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                                                        mapLibreInstance?.animateCamera(
                                                            CameraUpdateFactory.newLatLngZoom(
                                                                LatLng(lat, lng),
                                                                15.0
                                                            ),
                                                            800
                                                        )
                                                        android.widget.Toast.makeText(context, "Centered on ${dev.nickname} (GPS Fix)", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val myCoords = uiState.myCoordinates
                                                        if (myCoords != null) {
                                                            mapLibreInstance?.animateCamera(
                                                                CameraUpdateFactory.newLatLngZoom(
                                                                    LatLng(myCoords.latitude, myCoords.longitude),
                                                                    14.5
                                                                ),
                                                                800
                                                            )
                                                            android.widget.Toast.makeText(context, "Showing proximity for ${dev.nickname} on radar ring", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Selected ${dev.nickname} • Radio Link ${dev.hopCount} hop (${dev.rssi} dBm)", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(imageVector = Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(14.dp), tint = VyoraCyanSecondary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Locate", fontSize = 12.sp, color = VyoraCyanSecondary)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Button(
                                                onClick = {
                                                    showLiveDevicesSheet = false
                                                    onStartChat(dev.deviceId)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLiveDevicesSheet = false }) {
                        Text("Close", color = VyoraCyanLight)
                    }
                }
            )
        }
    }
}

/**
 * Updates markers on the MapLibreMap instance without creating memory leaks
 */
private fun refreshMapMarkers(
    map: MapLibreMap,
    uiState: MapUiState,
    onDeviceClick: (NearbyDevice) -> Unit
) {
    map.clear()

    // Add Self Node marker
    uiState.myCoordinates?.let { coords ->
        map.addMarker(
            org.maplibre.android.annotations.MarkerOptions()
                .position(LatLng(coords.latitude, coords.longitude))
                .title("YOU (Local Terminal)")
                .snippet("Self Node • Active GPS Fix")
        )
    }

    // Add Discovered Peer Markers
    uiState.filteredDevices.forEach { device ->
        val hasExactGps = device.latitude != null && device.longitude != null && (device.latitude != 0.0 || device.longitude != 0.0)
        val lat: Double
        val lng: Double
        val isEstimated: Boolean

        if (hasExactGps) {
            lat = device.latitude!!
            lng = device.longitude!!
            isEstimated = false
        } else if (uiState.myCoordinates != null) {
            // Project proximity on radar ring around local node using deterministic hash of deviceId
            val hash = Math.abs(device.deviceId.hashCode())
            val angleRad = Math.toRadians((hash % 360).toDouble())
            val radiusMeters = when (device.hopCount) {
                1 -> 80.0 + (hash % 60)
                2 -> 250.0 + (hash % 100)
                else -> 500.0 + (hash % 150)
            }
            val deltaLat = (radiusMeters * Math.cos(angleRad)) / 111320.0
            val deltaLng = (radiusMeters * Math.sin(angleRad)) / (111320.0 * Math.cos(Math.toRadians(uiState.myCoordinates.latitude)))
            lat = uiState.myCoordinates.latitude + deltaLat
            lng = uiState.myCoordinates.longitude + deltaLng
            isEstimated = true
        } else {
            val center = map.cameraPosition.target
            if (center != null && (center.latitude != 0.0 || center.longitude != 0.0)) {
                val hash = Math.abs(device.deviceId.hashCode())
                val angleRad = Math.toRadians((hash % 360).toDouble())
                val radiusMeters = 150.0 + (hash % 100)
                val deltaLat = (radiusMeters * Math.cos(angleRad)) / 111320.0
                val deltaLng = (radiusMeters * Math.sin(angleRad)) / (111320.0 * Math.cos(Math.toRadians(center.latitude)))
                lat = center.latitude + deltaLat
                lng = center.longitude + deltaLng
                isEstimated = true
            } else {
                lat = 0.0
                lng = 0.0
                isEstimated = true
            }
        }

        if (lat != 0.0 && lng != 0.0) {
            val title = if (isEstimated) "${device.nickname} (Radio Signal)" else device.nickname
            val snippet = "ID: ${device.deviceId} • RSSI: ${device.rssi}dBm • Hops: ${device.hopCount}${if (isEstimated) " [Estimated Proximity]" else " [GPS Fix]"}"
            map.addMarker(
                org.maplibre.android.annotations.MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(title)
                    .snippet(snippet)
            )
        }
    }
}
