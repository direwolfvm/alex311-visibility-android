package com.herbertindustries.alex311.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable fun MineScreen(app: AppState) {
    var addingWatch by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<Watch?>(null) }
    LaunchedEffect(app.signedIn) { if (app.signedIn) app.loadMine() }
    if (!app.signedIn) { SignInPrompt(app, "Follow requests, see what changed, and watch for new issues near places you care about."); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TitleBlock("Your follow-through", "My Issues", "What you reported. What you're watching.") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { app.launch { app.loadMine() } }, enabled = !app.mineLoading) { Text("Refresh") }
                FilledTonalButton(onClick = { app.launch { app.api.fire("POST", "submit/api/my/refresh"); app.loadMine(); app.message = "Checked your requests with the City." } }, enabled = !app.mineLoading) { Text("Ask the City now") }
            }
        }
        if (app.mineLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
        item { ErrorRetry(app.mineError) { app.launch { app.loadMine() } } }
        if (app.links.isEmpty() && !app.mineLoading && app.mineError == null) item { EmptyState(Icons.Outlined.BookmarkBorder, "Start following an issue", "Open any request and tap Follow. Reports you send will appear here too.") }
        listOf("Reported by me" to "mine", "Following" to "following").forEach { (title, relation) ->
            val rows = app.links.filter { if (relation == "mine") it.relation == "mine" else it.relation != "mine" }
            if (rows.isNotEmpty()) item { SectionTitle(title, Modifier.padding(horizontal = 20.dp)) }
            items(rows, key = { "link-${it.service_request_id}" }) { row -> Box(Modifier.padding(horizontal = 20.dp)) { RequestRow(row, app, changed = app.changed(row)) } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Your watches", Modifier.weight(1f)); TextButton(onClick = { addingWatch = true }) { Text("Add watch") }
            }
        }
        if (app.watches.isEmpty()) item { Text("Watch a request type, up to ten addresses, or an area for new reports.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium) }
        items(app.watches, key = { "watch-${it.watch_id}" }) { watch ->
            ListItem(headlineContent = { Text(watch.label ?: watchLabel(watch)) }, supportingContent = { Text(watch.kind.replaceFirstChar { it.uppercase() }) },
                trailingContent = { IconButton(onClick = { remove = watch }) { Icon(Icons.Outlined.DeleteOutline, "Remove watch") } })
        }
        if (app.feed.isNotEmpty()) {
            item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { SectionTitle("New matches", Modifier.weight(1f)); TextButton(onClick = { app.launch { app.api.fire("POST", "submit/api/feed/seen"); app.loadMine() } }) { Text("Mark seen") } } }
            items(app.feed, key = { "feed-${it.service_request_id}" }) { Box(Modifier.padding(horizontal = 20.dp)) { RequestRow(it, app) } }
        }
    }
    if (addingWatch) WatchPanel(app) { addingWatch = false }
    remove?.let { watch -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove this watch?") }, text = { Text(watch.label ?: watchLabel(watch)) },
        confirmButton = { TextButton(onClick = { remove = null; app.launch { app.api.fire("DELETE", "submit/api/watches/${watch.watch_id}"); app.loadMine() } }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
}
fun watchLabel(watch: Watch): String {
    val spec = watch.spec as? JsonObject
    return when (watch.kind) {
        "category" -> spec?.get("service_name")?.jsonPrimitive?.contentOrNull ?: "Request type"
        "address" -> (spec?.get("addresses") as? JsonArray)?.joinToString { it.jsonPrimitive.content } ?: "Addresses"
        else -> "Watched area"
    }
}
@Composable fun WatchPanel(app: AppState, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("Area") }
    var category by remember { mutableStateOf("") }
    var addresses by remember { mutableStateOf("") }
    var nearby by remember { mutableStateOf(true) }
    var radius by remember { mutableIntStateOf(400) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save() = scope.launch {
        if (busy) return@launch
        busy = true; error = null
        try {
            var label: String? = null
            val spec = when (kind) {
                "Area" -> {
                    val c = app.location.current() ?: error("Location unavailable. Enable it in Settings, or watch an address.")
                    require(Geo.inCity(c)) { "Choose an address in Alexandria when you are outside the city." }
                    label = "Within ${Geo.describe(radius.toDouble())} of where I was"
                    obj("polygon" to JsonArray(Geo.circle(c, radius.toDouble(), 16).map { JsonArray(listOf(JsonPrimitive(it.lng), JsonPrimitive(it.lat))) }))
                }
                "Addresses" -> {
                    val list = addresses.lines().map { it.trim() }.filter { it.isNotBlank() }
                    require(list.size in 1..10) { "Enter one to ten addresses, one per line." }
                    obj("addresses" to JsonArray(list.map(::JsonPrimitive)), "nearby" to nearby)
                }
                else -> { require(category.isNotBlank()) { "Choose a request type." }; obj("service_name" to category) }
            }
            app.api.fire("POST", "submit/api/watches", obj("kind" to when (kind) { "Area" -> "area"; "Addresses" -> "address"; else -> "category" }, "spec" to spec, "label" to label))
            app.loadMine(); close()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) } finally { busy = false }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.any { granted -> granted }) save() else error = "Allow location or watch an address instead." }
    FullScreenPanel("New watch", close) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChoiceRow(listOf("Area", "Addresses", "Type"), kind) { kind = it }
            when (kind) {
                "Area" -> { Text("Watch the area around your current location."); ChoiceRow(listOf("150 m", "400 m", "800 m"), "$radius m") { radius = it.substringBefore(' ').toInt() } }
                "Addresses" -> { Field(addresses, "Addresses, one per line", lines = 5) { addresses = it }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(nearby, { nearby = it }); Text("Include the block around each (150 m)") } }
                else -> {
                    Text("Every new request of this type, citywide.")
                    app.meta?.categories?.sortedBy { it.service_name }?.forEach { c -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(category == c.service_name, { category = c.service_name }); Text(c.service_name) } }
                }
            }
            error?.let { Notice(it, true) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { if (kind == "Area" && !app.location.allowed()) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) else save() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create watch") }
        }
    }
}
