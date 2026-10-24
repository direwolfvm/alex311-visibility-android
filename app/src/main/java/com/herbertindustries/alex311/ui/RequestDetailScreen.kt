package com.herbertindustries.alex311.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.Coordinate
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable fun RichText(raw: String) {
    // Compose's HTML conversion preserves links in registry banners and City notes.
    Text(androidx.compose.ui.text.AnnotatedString.fromHtml(raw), style = MaterialTheme.typography.bodyMedium)
}
@Composable fun RequestDetailScreen(id: String, app: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var row by remember(id) { mutableStateOf<Request?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var relation by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var rating by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, generation, app.signedIn) {
        loading = true; error = null
        try {
            row = app.api.request(id)
            app.markSeen(id)
            if (app.signedIn) {
                relation = app.api.get<JsonObject>("submit/api/link/$id")["relation"]?.jsonPrimitive?.contentOrNull
                feedback = app.api.get<FeedbackEnvelope>("submit/api/feedback/$id").feedback
            } else { relation = null; feedback = null }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) }
        finally { loading = false }
    }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.cases = app.cases.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(id, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { generation++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, app.api.url("r/$id").toString()), "Share request")) }) { Icon(Icons.Outlined.Share, "Share request") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorRetry(error) { generation++ }
        if (row == null && !loading) { WebButton("View this case on the website", app.api.url("r/$id").toString()) }
        row?.let { request ->
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    StatusPill(request.status)
                    Text(request.service_name ?: "Service request", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Text(request.address ?: "Address not available", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            if (!app.signedIn) app.account = true else scope.launch {
                                if (working) return@launch
                                working = true
                                try { if (relation == "following") app.api.fire("DELETE", "submit/api/follow/$id") else app.api.fire("POST", "submit/api/follow/$id"); generation++; app.loadMine() }
                                catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) } finally { working = false }
                            }
                        }, enabled = !working && relation != "mine") {
                            Icon(if (relation != null) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null); Spacer(Modifier.width(6.dp)); Text(when (relation) { "mine" -> "Your report"; "following" -> "Unfollow"; else -> "Follow" })
                        }
                        OutlinedButton(onClick = { if (app.signedIn) rating = true else app.account = true }) { Text("Was it fixed?") }
                    }
                }
                if (request.media.any { it.stored && (it.stored_mime ?: it.mime_type).orEmpty().startsWith("image/") }) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        request.media.filter { it.stored && (it.stored_mime ?: it.mime_type).orEmpty().startsWith("image/") }.forEach { media ->
                            AsyncImage(app.api.mediaUrl(media.media_id, 600), media.file_name ?: "Request photo", Modifier.size(220.dp).clickable { photo = media.media_id }, contentScale = ContentScale.Crop)
                        }
                    }
                }
                request.description?.takeIf { it.isNotBlank() }?.let { item { SectionTitle("What was reported"); RichText(it) } }
                request.status_notes?.takeIf { it.isNotBlank() }?.let { item { SectionTitle("What the City said"); RichText(it) } }
                request.closure_details?.takeIf { it.isNotBlank() }?.let { item { SectionTitle("Closure details"); RichText(it) } }
                item {
                    SectionTitle("Timeline")
                    DetailLine("Reported", dateLabel(request.requested_datetime))
                    request.last_updated_datetime?.let { DetailLine("Last updated", dateLabel(it)) }
                    request.expected_datetime?.let { DetailLine("Expected", dateLabel(it)) }
                    request.closed_datetime?.let { DetailLine("Closed", dateLabel(it)) }
                    (request.primary_service_department ?: request.agency_responsible)?.let { DetailLine("Department", it) }
                    request.priority?.let { DetailLine("Priority", it) }
                }
                if (request.lat != null && request.long != null) item {
                    val c = Coordinate(request.lat, request.long)
                    RequestMap(emptyList(), Modifier.fillMaxWidth().height(220.dp), center = c, zoom = 16, pin = c)
                    WebButton("Directions", "geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}")
                }
                if (request.resident_scores.isNotEmpty()) item {
                    SectionTitle("Residents' scores")
                    val values = request.resident_scores.mapNotNull { it.score }
                    Text(if (values.isEmpty()) "Residents are not sure yet." else "${"%.1f".format(values.average())} / 5 · ${values.size} ratings")
                    Text("The City's closure status and residents' experience can differ.", style = MaterialTheme.typography.bodySmall)
                }
                if (request.related.isNotEmpty() || request.parent_service_request_id != null || request.duplicate_parent_service_request_id != null) item { SectionTitle("Related requests") }
                listOfNotNull(request.parent_service_request_id, request.duplicate_parent_service_request_id).distinct().forEach { parent -> item { TextButton(onClick = { app.openCase(parent) }) { Text("Parent request $parent") } } }
                items(request.related) { related ->
                    TextButton(onClick = { app.openCase(related.service_request_id) }) { Text("${related.service_request_id} · ${related.service_name ?: related.relation}") }
                }
                item {
                    SectionTitle("Keep an eye on this")
                    TextButton(onClick = { if (!app.signedIn) app.account = true else app.launch { app.api.fire("POST", "submit/api/watches", obj("kind" to "category", "spec" to obj("service_name" to request.service_name))); app.loadMine(); app.message = "Watching this request type." } }) { Text("Watch this type of issue") }
                    request.address?.let { address -> TextButton(onClick = { if (!app.signedIn) app.account = true else app.launch { app.api.fire("POST", "submit/api/watches", obj("kind" to "address", "spec" to obj("addresses" to JsonArray(listOf(JsonPrimitive(address))), "nearby" to true))); app.loadMine(); app.message = "Watching this address." } }) { Text("Watch this address and block") } }
                    Button(onClick = { app.startReport(request.service_name, request.lat?.let { lat -> request.long?.let { Coordinate(lat, it) } }, request.address) }, modifier = Modifier.fillMaxWidth()) { Text("Report something similar here") }
                    WebButton("View on the website", app.api.url("r/$id").toString())
                }
            }
        }
    }
    if (rating) FeedbackPanel(id, feedback, app, { rating = false }) { rating = false; generation++ }
    photo?.let { mediaId -> FullScreenPanel("Request photo", { photo = null }) { padding ->
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        Box(Modifier.fillMaxSize().padding(padding).pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero } }) {
            AsyncImage(app.api.mediaUrl(mediaId), "Full size request photo. Pinch to zoom.", Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
        }
    } }
}
@Composable private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall) }
}
@Composable private fun FeedbackPanel(id: String, previous: Feedback?, app: AppState, close: () -> Unit, saved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var score by remember { mutableStateOf(previous?.score) }
    var note by remember { mutableStateOf(previous?.note.orEmpty()) }
    var share by remember { mutableStateOf(previous?.share_score ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(delete: Boolean) = scope.launch {
        if (busy) return@launch
        busy = true; error = null
        try {
            app.api.fire(if (delete) "DELETE" else "PUT", "submit/api/feedback/$id", if (delete) null else obj("score" to score, "note" to note.trim(), "share_score" to share)); saved()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) } finally { busy = false }
    }
    FullScreenPanel("Was it actually fixed?", close) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Your experience matters", style = MaterialTheme.typography.headlineSmall)
            Text("1 = not fixed · 5 = fully fixed. Your note is private; you can choose to share your score.")
            ChoiceRow(listOf("Not sure", "1", "2", "3", "4", "5"), score?.toString() ?: "Not sure") { score = it.toIntOrNull() }
            Field(note, "Private note", lines = 4) { note = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(share, { share = it }); Text("Share my score publicly") }
            error?.let { Notice(it, true) }
            Button(onClick = { run(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save feedback") }
            if (previous != null) TextButton(onClick = { run(true) }, enabled = !busy) { Text("Delete my feedback") }
        }
    }
}
