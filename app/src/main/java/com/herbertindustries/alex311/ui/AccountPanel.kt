package com.herbertindustries.alex311.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.herbertindustries.alex311.BuildConfig
import com.herbertindustries.alex311.data.*
import kotlinx.coroutines.*
import java.net.URI

@Composable fun OnboardingPanel(app: AppState) {
    FullScreenPanel("Welcome to Alex311", { app.finishOnboarding() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(Icons.Outlined.LocationCity, null, Modifier.size(76.dp), tint = MaterialTheme.colorScheme.primary)
            Text("A clearer view\nof your city.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Every 311 request in Alexandria — and what happens next.", style = MaterialTheme.typography.titleMedium)
            listOf(Icons.Outlined.NearMe to "See requests around you, with photos and updates from the City.", Icons.Outlined.AddComment to "Report a problem using the City's own questions. Get a real case number.", Icons.Outlined.Notifications to "Follow issues, watch your neighborhood, and say whether a problem was actually fixed.").forEach { (icon, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(text) }
            }
            Notice("An unofficial companion to Alex311. This app is not run by the City of Alexandria. Reports you send are real requests to the City.")
            Button(onClick = { app.finishOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text("Explore Alexandria") }
            OutlinedButton(onClick = { app.finishOnboarding(); app.account = true }, modifier = Modifier.fillMaxWidth()) { Text("Create an account or sign in") }
        }
    }
}

@Composable fun SignInPrompt(app: AppState, text: String) {
    EmptyState(Icons.Outlined.AccountCircle, "Make it yours", text) { Button(onClick = { app.account = true }) { Text("Create an account or sign in") } }
}

@Composable fun AccountPanel(app: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(app.session.pendingEmail) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(app.user?.display_name.orEmpty()) }
    var digest by remember { mutableStateOf<Digest?>(null) }
    var digestEmail by remember { mutableStateOf(app.user?.email.orEmpty()) }
    var server by remember { mutableStateOf(app.prefs.getString("server", "").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Status notifications enabled." else "Notifications are off. You can enable them in Android Settings." }
    fun run(block: suspend () -> Unit) { scope.launch {
        if (busy) return@launch
        busy = true; error = null; note = null
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) } finally { busy = false }
    } }
    LaunchedEffect(app.signedIn) {
        if (app.signedIn) try { digest = app.api.get("submit/api/digest"); digestEmail = digest?.email ?: digest?.account_email ?: app.user?.email.orEmpty() } catch (e: Exception) { if (e is CancellationException) throw e; error = friendly(e) }
    }
    FullScreenPanel(if (app.signedIn) "Account" else "Sign in", { app.account = false }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!app.signedIn) {
                Text("Your city. Your follow-through.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Sign in to report, follow requests, and watch your neighborhood. Your first sign-in creates your account.")
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                    OutlinedButton(onClick = { run { app.session.google(context); app.account = false; app.tab = Tab.MINE } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
                    HorizontalDivider()
                }
                Field(email, "Email address", type = KeyboardType.Email) { email = it }
                Button(onClick = { run {
                    if (app.session.heldCode != null) { app.session.completeOob(email, app.session.heldCode!!); app.account = false; app.tab = Tab.MINE }
                    else { app.session.sendLink(email); note = "Check $email for a sign-in link and a six-digit code." }
                } }, enabled = !busy && email.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (app.session.heldCode != null) "Complete sign-in" else "Email me a sign-in link") }
                SectionTitle("Already have an email?")
                Text("Use the newest six-digit code or paste its sign-in link. Links also work when wrapped by your email provider.", style = MaterialTheme.typography.bodySmall)
                Field(code, "Six-digit code or sign-in link", lines = 2) { code = it }
                OutlinedButton(onClick = { run { app.session.complete(email, code); app.account = false; app.tab = Tab.MINE } }, enabled = !busy && email.isNotBlank() && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Complete sign-in") }
            } else {
                Text(app.user?.email ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                Field(name, "Display name (optional)") { name = it.take(40) }
                TextButton(onClick = { run { app.api.fire("PUT", "submit/api/profile", obj("display_name" to name.trim().ifEmpty { null })); app.session.restore(); note = "Name saved." } }, enabled = !busy) { Text("Save name") }
                HorizontalDivider()
                SectionTitle("Email digest")
                Text("One email each morning when there is something new for what you watch.", style = MaterialTheme.typography.bodySmall)
                if (digest?.available == false) Text("Email is unavailable on this server.") else if (digest?.status == "on") {
                    Text("On · ${digest?.email.orEmpty()}")
                    TextButton(onClick = { run { app.api.fire("DELETE", "submit/api/digest"); digest = app.api.get("submit/api/digest"); note = "Digest turned off." } }, enabled = !busy) { Text("Turn off digest") }
                } else {
                    if (digest?.status == "pending") Notice("Confirm the link sent to ${digest?.email.orEmpty()}.")
                    Field(digestEmail, "Digest email", type = KeyboardType.Email) { digestEmail = it }
                    TextButton(onClick = { run { digest = app.api.call("PUT", "submit/api/digest", obj("email" to digestEmail.trim())); note = "Check your email for a confirmation link." } }, enabled = !busy && digestEmail.isNotBlank()) { Text("Turn on daily digest") }
                }
                HorizontalDivider()
                SectionTitle("Status notifications")
                Text("Android checks followed requests periodically. Delivery timing depends on Android's background scheduling.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Enable notifications") }
                TextButton(onClick = { app.account = false; app.onboarding = true }) { Text("Show the tour again") }
                HorizontalDivider()
                TextButton(onClick = { run { app.session.signOut(); app.account = false } }, enabled = !busy) { Text("Sign out") }
                TextButton(onClick = { confirm = "everywhere" }, enabled = !busy) { Text("Sign out on every device") }
                TextButton(onClick = { confirm = "delete" }, enabled = !busy) { Text("Delete my account", color = MaterialTheme.colorScheme.error) }
                Text("Deleting removes your list, watches, and ratings here. Requests already filed remain with the City.", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            note?.let { Notice(it) }; error?.let { Notice(it, true) }
            if (BuildConfig.DEBUG) {
                HorizontalDivider(); SectionTitle("Developer server")
                Field(server, "Server URL (blank = production)", type = KeyboardType.Uri) { server = it }
                TextButton(onClick = {
                    val target = server.trim().ifEmpty { Api.PRODUCTION }
                    val uri = runCatching { URI(target) }.getOrNull()
                    if (uri?.host != null && uri.userInfo == null && (uri.scheme == "https" || uri.scheme == "http" && uri.host in listOf("10.0.2.2", "localhost"))) {
                        if (target != app.api.baseUrl) run { app.session.signOut(); app.prefs.edit().putString("server", target).apply(); note = "Server saved. Close and relaunch the app to use it." }
                        else note = "This server is already active."
                    } else error = "Use an HTTPS server, or http://10.0.2.2:8311 for the emulator."
                }) { Text("Save server") }
            }
            WebButton("Alex311 Reborn website", app.api.baseUrl)
        }
    }
    confirm?.let { kind ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (kind == "delete") "Delete your account?" else "Sign out everywhere?") },
            text = { Text(if (kind == "delete") "Your list, watches, and ratings will be permanently removed. This cannot be undone." else "Every device will need to sign in again.") },
            confirmButton = { TextButton(onClick = { confirm = null; run { if (kind == "delete") app.session.deleteAccount() else app.session.signOut(true); app.account = false } }) { Text(if (kind == "delete") "Delete account" else "Sign out everywhere") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}
