package com.herbertindustries.alex311.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.herbertindustries.alex311.data.MapPoint
import com.herbertindustries.alex311.domain.Geo

@Composable fun NearbyScreen(app: AppState) {
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) app.locate() else app.message = "Location is off. You can explore Alexandria or choose an address when reporting."
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { TitleBlock("Your neighborhood", "What's happening nearby?", if (app.hasLocation) "Requests around your chosen location." else "Showing Old Town Alexandria. Use your location to see requests near you.") }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { if (app.location.allowed()) app.locate() else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }, enabled = !app.locating) {
                    Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(8.dp)); Text(if (app.locating) "Finding your location…" else "Use my location")
                }
                ChoiceRow(listOf("150 m", "400 m", "800 m"), "${app.radius} m") { app.radius = it.substringBefore(' ').toInt(); app.loadNearby() }
                Button(onClick = { app.startReport(coordinate = app.center) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Report an issue here")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(app.nearest, { app.nearest = !app.nearest }, { Text(if (app.nearest) "Nearest first" else "Newest first") })
                    FilterChip(app.nearbyMap, { app.nearbyMap = !app.nearbyMap }, { Text("Map") })
                    TextButton(onClick = { app.loadNearby() }) { Text("Refresh") }
                }
                ChoiceRow(listOf("30 days", "90 days", "1 year"), when (app.nearbyDays) { 90 -> "90 days"; 365 -> "1 year"; else -> "30 days" }) { app.nearbyDays = when (it) { "90 days" -> 90; "1 year" -> 365; else -> 30 }; app.loadNearby() }
                FilterChip(app.nearbyOpenOnly, { app.nearbyOpenOnly = !app.nearbyOpenOnly; app.loadNearby() }, { Text("Open only") })
                Text("${app.nearbyTotal} requests within ${Geo.describe(app.radius.toDouble())}", style = MaterialTheme.typography.labelMedium)
                if (app.nearby.size < app.nearbyTotal) Text("Nearest sorting applies to the ${app.nearby.size} loaded requests. Load more to include older reports.", style = MaterialTheme.typography.labelSmall)
                if (app.nearbyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        item { ErrorRetry(app.nearbyError) { app.loadNearby() } }
        if (app.nearbyMap) item {
            RequestMap(app.nearby.mapNotNull { row -> row.lat?.let { lat -> row.long?.let { MapPoint(row.service_request_id, lat, it, row.status) } } }, Modifier.fillMaxWidth().height(320.dp), center = app.center, zoom = 16, pin = app.center.takeIf { it != app.myLocation }, me = app.myLocation,
                onCase = app::openCase, onPick = { app.center = it; app.hasLocation = true; app.loadNearby() })
        }
        if (app.nearby.isEmpty() && !app.nearbyLoading && app.nearbyError == null) item { EmptyState(Icons.Outlined.CheckCircle, "Nothing reported here", "Try a larger distance or explore the city.") }
        items(app.sortedNearby, key = { it.service_request_id }) { row -> Box(Modifier.padding(horizontal = 20.dp)) { RequestRow(row, app, app.center) } }
        if (app.nearby.size < app.nearbyTotal) item { TextButton(onClick = { app.loadNearby(true) }, enabled = !app.nearbyLoading, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
    }
}
