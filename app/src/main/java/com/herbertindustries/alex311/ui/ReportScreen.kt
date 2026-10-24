package com.herbertindustries.alex311.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime

@Composable fun ReportScreen(app: AppState) {
    val report = app.report
    LaunchedEffect(Unit, app.signedIn) { if (app.signedIn && report.services.isEmpty()) report.load() }
    BackHandler(enabled = report.draft.step in 1..3 && !report.busy) { report.step(report.draft.step - 1) }
    if (!app.signedIn) { SignInPrompt(app, "Sign in to send a real service request to the City and receive its case number."); return }
    Column(Modifier.fillMaxSize()) {
        val titles = listOf("What needs attention?", "Where is the issue?", "Tell the City more", "Review & send", "Filing status")
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (report.draft.step in 1..3) IconButton(onClick = { report.step(report.draft.step - 1) }, enabled = !report.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous step") }
            Column(Modifier.weight(1f).padding(start = if (report.draft.step in 1..3) 0.dp else 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text(if (report.draft.step == 4) "SENT TO THE FILING SERVICE" else "STEP ${report.draft.step + 1} OF 4", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(titles.getOrElse(report.draft.step) { "Report" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(progress = { ((report.draft.step + 1) / 4f).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
        if (report.loading || report.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        report.error?.let { Notice(it, true, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        when (report.draft.step) {
            0 -> ServiceStep(report)
            1 -> LocationStep(report, app)
            2 -> DetailsStep(report)
            3 -> ReviewStep(report)
            4 -> StatusStep(report, app)
        }
    }
}
@Composable private fun ServiceStep(report: ReportState) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Choose the type of issue. Common requests appear first.", style = MaterialTheme.typography.bodyMedium)
            Field(report.search, "Search request types") { report.search = it }
        }
        if (report.services.isEmpty() && !report.loading) item { TextButton(onClick = { report.load() }) { Text("Load request types") } }
        items(report.matches, key = { it.service_code }) { service ->
            Surface(onClick = { report.choose(service) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(service.service_name, style = MaterialTheme.typography.titleMedium)
                    service.description?.let { Text(android.text.Html.fromHtml(it, 0).toString(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
@Composable private fun LocationStep(report: ReportState, app: AppState) {
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.any { allowed -> allowed }) app.locate { coordinate -> report.pin(coordinate) } else app.message = "Location permission is off. Search an address or tap the map." }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(report.service?.service_name ?: "Selected request", style = MaterialTheme.typography.titleMedium) }
        item { Field(report.draft.address, "House number and street") { report.address(it) } }
        items(report.candidates, key = { it.address }) { candidate ->
            OutlinedButton(onClick = { report.pick(candidate) }, modifier = Modifier.fillMaxWidth()) { Text(candidate.address) }
        }
        item {
            FilledTonalButton(onClick = { if (app.location.allowed()) app.locate { coordinate -> report.pin(coordinate) } else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }, enabled = !app.locating && !report.busy) { Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(8.dp)); Text(if (app.locating) "Finding location…" else "Use my location") }
            Text("Tap the map to place a pin. The address is looked up in the City's records.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            RequestMap(emptyList(), Modifier.fillMaxWidth().height(300.dp), center = report.draft.coordinate ?: Geo.city, zoom = 16, pin = report.draft.coordinate, onPick = { if (!report.busy) report.pin(it) })
        }
        report.draft.coordinate?.let { coordinate -> item {
            Text("${"%.6f".format(java.util.Locale.US, coordinate.lat)}, ${"%.6f".format(java.util.Locale.US, coordinate.lng)}", style = MaterialTheme.typography.labelMedium)
            if (!Geo.inCity(coordinate)) Notice("This location is outside Alexandria. Choose a place within the city.", true)
            else if (report.draft.address.isBlank()) Notice("No nearby address was found. The pin will identify this location.")
        } }
        if (report.nearby.isNotEmpty()) {
            item { SectionTitle("Already reported nearby?"); Text("Review similar requests before sending a duplicate.", style = MaterialTheme.typography.bodySmall) }
            items(report.nearby) { row ->
                Surface(onClick = { app.openCase(row.service_request_id) }, tonalElevation = 2.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        StatusPill(row.status); Text(row.address ?: row.service_request_id); Text("${row.service_request_id} · ${Geo.describe(row.meters.toDouble())}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { Button(onClick = { report.step(2) }, enabled = report.readyLocation && !report.busy, modifier = Modifier.fillMaxWidth()) { Text("Continue to details") } }
    }
}
@Composable private fun DetailsStep(report: ReportState) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { report.addPhotos(it) }
    val check = report.logic.check(report.draft.answers)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        report.service?.banners?.forEach { banner -> item { RichText(banner) } }
        items(report.logic.visible(report.draft.answers), key = { it.key }) { question -> QuestionInput(question, report.draft.answers[question.key]) { report.answer(question.key, it) } }
        items(check.blocking + check.advisories + check.info) { Notice(it.text, it in check.blocking) }
        item { Field(report.draft.description, "Additional details (optional)", lines = 4) { text -> report.edit { it.copy(description = text) } } }
        item {
            SectionTitle("Photos (${report.draft.photos.size}/3)")
            Text("Photos stay on this device until you send. Location metadata is removed.", style = MaterialTheme.typography.bodySmall)
            if (report.draft.photos.size < 3) OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !report.busy) { Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Add photos") }
        }
        items(report.draft.photos, key = { it }) { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(report.photoFile(name), "Attached report photo", Modifier.size(100.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                TextButton(onClick = { report.removePhoto(name) }, enabled = !report.busy) { Text("Remove photo") }
            }
        }
        if (check.missing.isNotEmpty()) item { Text("${check.missing.size} required question(s) still need an answer.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        item { Button(onClick = { report.step(3) }, enabled = check.canContinue && !report.busy, modifier = Modifier.fillMaxWidth()) { Text("Review report") } }
    }
}
@Composable private fun QuestionInput(q: Question, answer: JsonElement?, set: (JsonElement) -> Unit) {
    val context = LocalContext.current
    val selected = FormLogic(emptyList()).selected(answer)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(android.text.Html.fromHtml(q.text, 0).toString() + if (q.required) " *" else "", style = MaterialTheme.typography.titleSmall)
        q.note?.let { RichText(it) }
        when (q.input) {
            Input.SINGLE, Input.MULTIPLE -> q.choosableOptions.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable {
                    set(if (q.input == Input.MULTIPLE) JsonArray((if (option.value in selected) selected - option.value else selected + option.value).sorted().map(::JsonPrimitive)) else JsonPrimitive(option.value))
                }, verticalAlignment = Alignment.CenterVertically) {
                    if (q.input == Input.MULTIPLE) Checkbox(option.value in selected, null) else RadioButton(option.value in selected, null)
                    Text(option.value, Modifier.padding(vertical = 10.dp, horizontal = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Input.TEXT -> Field((answer as? JsonPrimitive)?.contentOrNull.orEmpty(), "Your answer", lines = 2) { set(JsonPrimitive(it)) }
            Input.DATE_TIME -> {
                val value = answer as? JsonObject ?: FormLogic.dateAnswer(java.time.Instant.now())
                val date = runCatching { LocalDate.parse(value["date"]!!.jsonPrimitive.content) }.getOrDefault(LocalDate.now())
                val time = runCatching { LocalTime.parse(value["time"]!!.jsonPrimitive.content) }.getOrDefault(LocalTime.NOON)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> set(JsonObject(value + ("date" to JsonPrimitive(LocalDate.of(year, month + 1, day).toString())))) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text(date.toString()) }
                    OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> set(JsonObject(value + ("time" to JsonPrimitive("%02d:%02d".format(java.util.Locale.US, hour, minute))))) }, time.hour, time.minute, false).show() }) { Text(time.toString()) }
                }
                Text("Alexandria time (America/New_York)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable private fun ReviewStep(report: ReportState) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(report.service?.service_name.orEmpty()); Text(report.draft.cityAddress ?: report.draft.address.ifBlank { "Pin on the map" }) }
        items(report.logic.visible(report.draft.answers)) { q ->
            report.draft.answers[q.key]?.let { answer -> Column { Text(android.text.Html.fromHtml(q.text, 0).toString(), style = MaterialTheme.typography.labelMedium); Text(displayAnswer(answer), style = MaterialTheme.typography.bodyMedium) } }
        }
        if (report.draft.description.isNotBlank()) item { Text(report.draft.description) }
        item { Text("${report.draft.photos.size} photos attached", style = MaterialTheme.typography.labelMedium); HorizontalDivider(); SectionTitle("Your contact details") }
        item { Field(report.contact.first_name, "First name") { report.contact = report.contact.copy(first_name = it) } }
        item { Field(report.contact.last_name, "Last name") { report.contact = report.contact.copy(last_name = it) } }
        item { Field(report.contact.email, "Email", type = KeyboardType.Email) { report.contact = report.contact.copy(email = it) } }
        item { Field(report.contact.phone, "Phone", type = KeyboardType.Phone) { report.contact = report.contact.copy(phone = it) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(report.rememberContact, { report.rememberContact = it }); Text("Remember on this phone") }
            Text("The City may contact you. This site passes your details to the City and deletes them after filing. Remembered details are encrypted on this device.", style = MaterialTheme.typography.bodySmall)
        }
        report.validation?.let { v -> items(v.errors + v.blocking + v.advisories + v.info) { Notice(it.message, it in v.errors || it in v.blocking) } }
        report.precheck?.let { p ->
            item { Notice(when (p.outcome) { "block" -> "This report cannot be sent."; "review" -> "This may be a duplicate. Review the findings before sending."; else -> "Checked and ready to send." }, p.outcome == "block") }
            items(p.findings) { Notice(it.message, it.outcome == "block") }
            if (p.outcome == "review") item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(report.acknowledged, { report.acknowledged = it }); Text("I reviewed the findings and want to send this report.") } }
        }
        item {
            if (report.precheck == null || report.validation?.ok != true) Button(onClick = { report.check() }, enabled = !report.busy, modifier = Modifier.fillMaxWidth()) { Text("Check before sending") }
            else Button(onClick = { confirming = true }, enabled = report.canSend, modifier = Modifier.fillMaxWidth()) { Text(if (report.precheck?.outcome == "review") "Send it anyway" else "Send to the City") }
            if (!report.contact.complete) Text("Fill in all four contact details to send.", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Alex311 report", report.summary()))
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://alex311.alexandriava.gov/customer/s/")))
            }) { Text("Copy details and file on the City's portal") }
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Send this to the City?") },
        text = { Text("This is a real service request to the City of Alexandria. Staff may be dispatched. There is no review step in between.") },
        confirmButton = { TextButton(onClick = { confirming = false; report.send(); if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Send to the City") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}
@Composable private fun StatusStep(report: ReportState, app: AppState) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(if (report.attempt?.submit_state == "filed") Icons.Outlined.CheckCircle else Icons.Outlined.Send, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(report.attempt?.submit_state?.replaceFirstChar { it.uppercase() } ?: "Checking the filing service…", style = MaterialTheme.typography.headlineMedium)
        report.attempt?.city_case_number?.let { id ->
            Text("City case number", style = MaterialTheme.typography.labelLarge)
            Text(id, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { app.openCase(id) }) { Text("Open request") }
        }
        report.attempt?.message?.let { Notice(it) }
        report.attempt?.error?.let { Notice(it, true) }
        report.attempt?.photo_note?.let { Notice(it) }
        report.pollNote?.let { Notice(it) }
        Text("Filing can take a minute. Your report will appear in My Issues once the City accepts it. Android checks followed cases periodically.")
        OutlinedButton(onClick = { report.resume() }) { Text("Refresh filing status") }
        if (report.attempt?.submit_state == "prepared") OutlinedButton(onClick = { report.returnToReview() }) { Text("Return to review") }
        Button(onClick = { app.tab = Tab.MINE; app.launch { app.loadMine() } }) { Text("Go to My Issues") }
        if (report.attempt?.isFinal == true) TextButton(onClick = { report.reset() }) { Text("Start another report") }
    }
}
private fun displayAnswer(answer: JsonElement): String = when (answer) {
    is JsonPrimitive -> answer.content
    is JsonArray -> answer.joinToString(", ") { it.jsonPrimitive.content }
    is JsonObject -> "${answer["date"]?.jsonPrimitive?.content.orEmpty()} ${answer["time"]?.jsonPrimitive?.content.orEmpty()}"
}
