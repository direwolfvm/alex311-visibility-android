package com.herbertindustries.alex311.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.herbertindustries.alex311.data.Request
import com.herbertindustries.alex311.domain.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Teal = Color(0xFF006D77)
val Amber = Color(0xFFAC5B13)
@Composable fun Alex311Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF80D3D8), primaryContainer = Color(0xFF174B50), secondaryContainer = Color(0xFF514332), secondary = Color(0xFFF2BC83), background = Color(0xFF111919), surface = Color(0xFF182323))
    else lightColorScheme(primary = Teal, primaryContainer = Color(0xFFD5ECEB), secondary = Amber, secondaryContainer = Color(0xFFF1E5D2), background = Color(0xFFF7F8F4), surface = Color.White, surfaceVariant = Color(0xFFEDF1EB)), content = content)
}
@Composable fun TitleBlock(eyebrow: String, title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.7.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun SectionTitle(title: String, modifier: Modifier = Modifier) { Text(title, modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
@Composable fun Notice(text: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
@Composable fun EmptyState(icon: ImageVector, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
@Composable fun ErrorRetry(error: String?, onRetry: () -> Unit) {
    if (error != null) Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Notice(error, true); TextButton(onClick = onRetry) { Text("Try again") } }
}
@Composable fun StatusPill(raw: String?) {
    val color = when (raw?.lowercase()) { "closed" -> Teal; "open" -> Amber; else -> Color.Gray }
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) {
        Text(raw?.replaceFirstChar { it.uppercase() } ?: "Unknown", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable fun RequestRow(row: Request, app: AppState, center: Coordinate? = null, changed: Boolean = false) {
    Surface(onClick = { app.openCase(row.service_request_id) }, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            row.media_ids.firstOrNull()?.let { AsyncImage(app.api.mediaUrl(it, 160), "Request photo", Modifier.size(62.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(row.status)
                    if (changed || row.is_new) Text("NEW", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(row.service_request_id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(row.service_name ?: "Service request", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(row.address ?: "Location not available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val distance = if (center != null && row.lat != null && row.long != null) Geo.describe(Geo.distance(center, Coordinate(row.lat, row.long))) + " · " else ""
                Text(distance + dateLabel(row.requested_datetime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, "Open request", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
fun dateLabel(raw: String?): String = parseInstant(raw)?.atZone(ZoneId.of("America/New_York"))?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Date unavailable"
@Composable fun Field(value: String, label: String, modifier: Modifier = Modifier, type: KeyboardType = KeyboardType.Text, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = lines == 1, minLines = lines,
        keyboardOptions = KeyboardOptions(keyboardType = type), shape = RoundedCornerShape(12.dp))
}
@Composable fun ChoiceRow(labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { FilterChip(selected == it, { onSelect(it) }, { Text(it) }) }
    }
}
@Composable fun WebButton(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text(label) }
}
@Composable fun FullScreenPanel(title: String, close: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = close, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(topBar = { @OptIn(ExperimentalMaterial3Api::class) TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }, content = content)
        }
    }
}
