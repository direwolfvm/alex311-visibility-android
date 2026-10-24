package com.herbertindustries.alex311.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Alex311Root(app: AppState) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) { app.message?.let { snackbar.showSnackbar(it); app.message = null } }
    BackHandler(enabled = app.cases.isNotEmpty()) { app.cases = app.cases.dropLast(1) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if (app.cases.isEmpty()) CenterAlignedTopAppBar(title = { Text("Alex311 Reborn", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.padding(start = 20.dp), tint = MaterialTheme.colorScheme.primary) },
            actions = { IconButton(onClick = { app.account = true }) { Icon(Icons.Outlined.AccountCircle, "Account") } })
    }, bottomBar = {
        if (app.cases.isEmpty()) NavigationBar {
            Tab.entries.forEach { tab ->
                val icon = when (tab) { Tab.NEARBY -> Icons.Outlined.NearMe; Tab.REPORT -> Icons.Outlined.AddComment; Tab.MINE -> Icons.Outlined.Notifications; Tab.EXPLORE -> Icons.Outlined.Map }
                NavigationBarItem(selected = app.tab == tab, onClick = { app.tab = tab }, label = { Text(tab.label) }, icon = {
                    if (tab == Tab.MINE && app.unseenCount > 0) BadgedBox(badge = { Badge { Text("${app.unseenCount}") } }) { Icon(icon, tab.label) } else Icon(icon, tab.label)
                })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val case = app.cases.lastOrNull()
            if (case != null) key(case) { RequestDetailScreen(case, app) }
            else when (app.tab) {
                Tab.NEARBY -> NearbyScreen(app)
                Tab.REPORT -> ReportScreen(app)
                Tab.MINE -> MineScreen(app)
                Tab.EXPLORE -> ExploreScreen(app)
            }
        }
    }
    if (app.account) AccountPanel(app)
    if (app.onboarding) OnboardingPanel(app)
}
