package com.herbertindustries.alex311.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.herbertindustries.alex311.data.Analytics
import kotlinx.coroutines.delay
import java.util.Locale

@Composable fun ExploreScreen(app: AppState) {
    var filters by remember { mutableStateOf(false) }
    LaunchedEffect(app.exploreMode, app.query, app.status, app.categories, app.window, app.sort, app.area) { delay(350); app.loadExplore() }
    Column(Modifier.fillMaxSize()) {
        TitleBlock("Across Alexandria", "Explore the city", "${app.total} requests · a public view of what gets reported.")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(app.query, "Search case, address, or description") { app.query = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ChoiceRow(listOf("Map", "List", "Stats"), app.exploreMode) { app.exploreMode = it } }
                IconButton(onClick = { filters = true }) { Icon(Icons.Outlined.Tune, "Filters") }
                IconButton(onClick = { app.loadExplore() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            }
            if (app.area != null) TextButton(onClick = { app.area = null }) { Text("Clear map area filter") }
            if (app.exploreLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        ErrorRetry(app.exploreError) { app.loadExplore() }
        when (app.exploreMode) {
            "Map" -> {
                Row(Modifier.padding(horizontal = 20.dp)) { ChoiceRow(listOf("Status", "Age", "Type"), app.colorBy) { app.colorBy = it } }
                Text(when (app.colorBy) { "Age" -> "Teal: under 30 days · amber: 30–89 · red: 90+"; "Type" -> "Requests are colored by service type"; else -> "Amber: open · teal: closed · gray: other" }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                RequestMap(app.points, Modifier.fillMaxWidth().weight(1f), colorBy = app.colorBy, onCase = app::openCase, onArea = { app.area = it })
            }
            "List" -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ChoiceRow(listOf("Newest", "Updated", "Photos", "Type", "Address"), when (app.sort) { "updated" -> "Updated"; "photos" -> "Photos"; "category" -> "Type"; "address" -> "Address"; else -> "Newest" }) {
                    app.sort = when (it) { "Updated" -> "updated"; "Photos" -> "photos"; "Type" -> "category"; "Address" -> "address"; else -> "requested" }
                } }
                if (app.rows.isEmpty() && !app.exploreLoading && app.exploreError == null) item { EmptyState(Icons.Outlined.SearchOff, "No matching requests", "Try another search or clear some filters.") }
                items(app.rows, key = { it.service_request_id }) { RequestRow(it, app) }
                if (app.rows.size < app.total) item { TextButton(onClick = { app.loadExplore(true) }, enabled = !app.exploreLoading, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
            }
            "Stats" -> app.analytics?.let { AnalyticsContent(it, Modifier.weight(1f)) }
        }
    }
    if (filters) FullScreenPanel("Explore filters", { filters = false }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Time window")
            ChoiceRow(listOf("30 days", "3 months", "Year", "All time"), when (app.window) { 30 -> "30 days"; 365 -> "Year"; 0 -> "All time"; else -> "3 months" }) { app.window = when (it) { "30 days" -> 30; "Year" -> 365; "All time" -> 0; else -> 91 } }
            Text("Measured back from the newest record, so the window still works if ingestion pauses.", style = MaterialTheme.typography.bodySmall)
            SectionTitle("Status")
            ChoiceRow(listOf("All", "Open", "Closed"), app.status?.replaceFirstChar { it.uppercase() } ?: "All") { app.status = if (it == "All") null else it.lowercase() }
            SectionTitle("Request types")
            TextButton(onClick = { app.categories = emptyList() }) { Text("All types") }
            app.meta?.categories?.sortedBy { it.service_name }?.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(category.service_name in app.categories, { selected -> app.categories = if (selected) app.categories + category.service_name else app.categories - category.service_name })
                    Text("${category.service_name} (${category.n})", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = { filters = false }, modifier = Modifier.fillMaxWidth()) { Text("Show results") }
        }
    }
}

@Composable fun AnalyticsContent(data: Analytics, modifier: Modifier = Modifier) {
    val summary = data.summary
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total", "${summary.total}", Modifier.weight(1f)); StatCard("Open", "${summary.open}", Modifier.weight(1f)); StatCard("Closed", "${summary.closed}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Median days to close", number(summary.median_ttc), Modifier.weight(1f)); StatCard("Average open age", number(summary.avg_open_age), Modifier.weight(1f))
            }
            Text("Time to close uses the City's closed records. A closed case does not always mean the issue was fixed.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        item { SectionTitle("Requests by type") }
        val maxCategory = data.by_category.maxOfOrNull { it.n }?.coerceAtLeast(1) ?: 1
        items(data.by_category.take(15)) { c -> BarRow(c.service_name, "${c.n} · ${c.open_n} open", c.n.toFloat() / maxCategory) }
        item { SectionTitle("Time to close") }
        val maxBucket = data.ttc_histogram.maxOfOrNull { it.n }?.coerceAtLeast(1) ?: 1
        items(data.ttc_histogram) { bucket -> BarRow(when (bucket.bucket) { 0 -> "Under 1 day"; 1 -> "1–3 days"; 2 -> "3–7 days"; 3 -> "7–14 days"; 4 -> "14–30 days"; 5 -> "30–90 days"; else -> "90+ days" }, "${bucket.n}", bucket.n.toFloat() / maxBucket) }
        item { SectionTitle("Weekly activity"); Text("Opened / closed", style = MaterialTheme.typography.labelSmall) }
        val maxWeek = data.weekly.maxOfOrNull { maxOf(it.opened, it.closed) }?.coerceAtLeast(1.0) ?: 1.0
        items(data.weekly.takeLast(16)) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${dateLabel(week.week)} · ${week.opened.toInt()} opened / ${week.closed.toInt()} closed", style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { (week.opened / maxWeek).toFloat() }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                LinearProgressIndicator(progress = { (week.closed / maxWeek).toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { SectionTitle("Day of week") }
        val maxDay = data.weekday.maxOfOrNull { it.n }?.coerceAtLeast(1) ?: 1
        items(data.weekday) { day -> BarRow(listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").getOrElse(day.dow) { "Day ${day.dow}" }, "${day.n}", day.n.toFloat() / maxDay) }
    }
}
@Composable private fun StatCard(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), tonalElevation = 2.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun BarRow(label: String, value: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.labelSmall) }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
    }
}
private fun number(value: Double?) = value?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
