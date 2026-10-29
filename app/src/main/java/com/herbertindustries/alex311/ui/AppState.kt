package com.herbertindustries.alex311.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herbertindustries.alex311.Alex311Application
import com.herbertindustries.alex311.background.StatusWorker
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import java.time.*

enum class Tab(val label: String) { NEARBY("Near Me"), REPORT("Report"), MINE("My Issues"), EXPLORE("Explore") }
class AppState(application: Application): AndroidViewModel(application) {
    val app = application as Alex311Application
    val api = app.api
    val session = app.session
    val prefs = app.getSharedPreferences("settings", Application.MODE_PRIVATE)
    val location = DeviceLocation(app)
    val report = ReportState(app, viewModelScope)
    var tab by mutableStateOf(Tab.NEARBY)
    var cases by mutableStateOf(emptyList<String>())
    var account by mutableStateOf(false)
    var onboarding by mutableStateOf(!prefs.getBoolean("onboardingDone", false))
    var message by mutableStateOf<String?>(null)
    var signedIn by mutableStateOf(session.signedIn.value)
    var user by mutableStateOf<User?>(null)
    var meta by mutableStateOf<Meta?>(null)
    var center by mutableStateOf(Geo.city)
    var hasLocation by mutableStateOf(false)
    /** The device's last known position, kept apart from [center], which can be a place picked on the map. */
    var myLocation by mutableStateOf<Coordinate?>(null)
    var radius by mutableIntStateOf(400)
    var nearbyDays by mutableIntStateOf(30)
    var nearbyOpenOnly by mutableStateOf(false)
    var nearest by mutableStateOf(true)
    var nearby by mutableStateOf(emptyList<Request>())
    var nearbyTotal by mutableIntStateOf(0)
    var nearbyLoading by mutableStateOf(false)
    var nearbyError by mutableStateOf<String?>(null)
    var nearbyMap by mutableStateOf(false)
    var locating by mutableStateOf(false)
    var query by mutableStateOf("")
    var status by mutableStateOf<String?>(null)
    var categories by mutableStateOf(emptyList<String>())
    var window by mutableIntStateOf(91)
    var sort by mutableStateOf("requested")
    var exploreMode by mutableStateOf("Map")
    var colorBy by mutableStateOf("Status")
    var area by mutableStateOf<List<Coordinate>?>(null)
    var points by mutableStateOf(emptyList<MapPoint>())
    var rows by mutableStateOf(emptyList<Request>())
    var total by mutableIntStateOf(0)
    var analytics by mutableStateOf<Analytics?>(null)
    var exploreLoading by mutableStateOf(false)
    var exploreError by mutableStateOf<String?>(null)
    var links by mutableStateOf(emptyList<Request>())
    var watches by mutableStateOf(emptyList<Watch>())
    var feed by mutableStateOf(emptyList<Request>())
    var mineLoading by mutableStateOf(false)
    var mineError by mutableStateOf<String?>(null)
    var seen by mutableStateOf(runCatching { WireJson.decodeFromString<Map<String, String>>(prefs.getString("seenStatuses", "{}").orEmpty()) }.getOrDefault(emptyMap()))
    fun changed(row: Request) = row.service_request_id in seen && seen[row.service_request_id] != row.status.orEmpty()
    val unseenCount: Int get() = links.count(::changed)
    private var nearJob: Job? = null
    private var exploreJob: Job? = null
    init {
        viewModelScope.launch { session.signedIn.collectLatest {
            signedIn = it
            if (it) { StatusWorker.schedule(app); loadMine() } else { links = emptyList(); watches = emptyList(); feed = emptyList(); seen = emptyMap(); report.clearPrivateState() }
        } }
        viewModelScope.launch { session.user.collect { user = it } }
        launch { session.restore() }
        launch { meta = api.meta() }
        loadNearby()
    }
    fun launch(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = friendly(e) }
    }
    fun finishOnboarding() { prefs.edit().putBoolean("onboardingDone", true).apply(); onboarding = false }
    fun openCase(id: String) { cases = cases + id; markSeen(id) }
    fun markSeen(id: String) {
        links.firstOrNull { it.service_request_id == id }?.let { seen = seen + (id to it.status.orEmpty()); prefs.edit().putString("seenStatuses", WireJson.encodeToString(seen)).apply() }
    }
    fun startReport(service: String? = null, coordinate: Coordinate? = null, address: String? = null) {
        cases = emptyList(); tab = Tab.REPORT
        report.seed(service, coordinate, address)
    }
    fun handleLink(raw: String) {
        when (val destination = Links.destination(raw)) {
            is Links.Destination.SignIn -> {
                account = true
                destination.code?.let { code ->
                    session.heldCode = code
                    if (session.pendingEmail.isNotBlank()) launch { session.completeOob(session.pendingEmail, code); account = false; tab = Tab.MINE }
                }
            }
            is Links.Destination.Case -> openCase(destination.id)
            Links.Destination.Mine -> { cases = emptyList(); tab = Tab.MINE }
            Links.Destination.Report -> startReport()
            Links.Destination.Nearby -> { cases = emptyList(); tab = Tab.NEARBY; if (location.allowed()) locate() }
            null -> Unit
        }
    }
    fun locate(onLocated: ((Coordinate) -> Unit)? = null) = launch {
        if (locating) return@launch
        locating = true
        try {
            val c = location.current() ?: error("Location is unavailable. Enable location in Android Settings or choose a place on the map.")
            myLocation = c
            if (onLocated != null) onLocated(c) else {
                center = if (Geo.inCity(c)) c else Geo.city
                hasLocation = Geo.inCity(c)
                if (!hasLocation) message = "You are outside Alexandria. Showing Old Town; choose a city address when reporting."
                loadNearby()
            }
        } finally { locating = false }
    }
    fun loadNearby(more: Boolean = false) {
        if (more && nearbyLoading) return
        nearJob?.cancel()
        nearJob = viewModelScope.launch {
            nearbyLoading = true; nearbyError = null
            try {
                val page = api.requests(RequestFilter(start = Instant.now().minusSeconds(nearbyDays * 86400L).atZone(ZoneId.of("America/New_York")).toLocalDate().toString(), status = if (nearbyOpenOnly) "open" else null, polygon = Geo.circle(center, radius.toDouble())), offset = if (more) nearby.size else 0, limit = 300)
                nearby = (if (more) nearby else emptyList()) + page.rows
                nearbyTotal = page.total
            } catch (e: CancellationException) { throw e } catch (e: Exception) { nearbyError = friendly(e) }
            finally { nearbyLoading = false }
        }
    }
    val sortedNearby get() = if (nearest) nearby.sortedBy { row -> row.lat?.let { lat -> row.long?.let { lng -> Geo.distance(center, Coordinate(lat, lng)) } } ?: Double.MAX_VALUE } else nearby
    fun filter(): RequestFilter {
        val anchor = parseInstant(meta?.max_date) ?: Instant.now()
        return RequestFilter(start = if (window == 0) null else anchor.minusSeconds(window * 86400L).atZone(ZoneId.of("America/New_York")).toLocalDate().toString(), categories = categories, status = status, query = query, polygon = area)
    }
    fun loadExplore(more: Boolean = false) {
        if (more && exploreLoading) return
        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            exploreLoading = true; exploreError = null
            try {
                if (meta == null) meta = api.meta()
                val filter = filter()
                when (exploreMode) {
                    "Map" -> { points = api.points(filter); total = points.size }
                    "Stats" -> { analytics = api.analytics(filter); total = analytics!!.summary.total }
                    else -> { val page = api.requests(filter, sort, if (more) rows.size else 0); rows = (if (more) rows else emptyList()) + page.rows; total = page.total }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { exploreError = friendly(e) }
            finally { exploreLoading = false }
        }
    }
    suspend fun loadMine() {
        if (!signedIn) return
        mineLoading = true; mineError = null
        try {
            coroutineScope {
                val list = async { api.get<MyLinks>("submit/api/my").links }
                val watch = async { api.get<WatchList>("submit/api/watches").watches }
                val newFeed = async { api.get<Feed>("submit/api/feed").items }
                links = list.await(); watches = watch.await(); feed = newFeed.await()
            }
            val baseline = StatusChanges.snapshot(links)
            seen = baseline.filterKeys { it !in seen } + seen
            prefs.edit().putString("seenStatuses", WireJson.encodeToString(seen)).apply()
            if (!prefs.contains("statusSnapshot")) prefs.edit().putString("statusSnapshot", WireJson.encodeToString(baseline)).apply()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { mineError = friendly(e) }
        finally { mineLoading = false }
    }
}
fun parseInstant(raw: String?): Instant? = raw?.let { text -> runCatching { Instant.parse(text) }.getOrNull() ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull() ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T')).atZone(ZoneId.of("America/New_York")).toInstant() }.getOrNull() ?: runCatching { LocalDate.parse(text).atStartOfDay(ZoneId.of("America/New_York")).toInstant() }.getOrNull() }
fun friendly(e: Throwable): String = when (e) {
    is java.net.UnknownHostException -> "You're offline. Check your connection and try again."
    is java.net.ConnectException, is java.net.NoRouteToHostException -> "Couldn't reach the server. Check your connection and try again."
    is java.net.SocketTimeoutException -> "The server took too long to answer. Try again."
    is kotlinx.serialization.SerializationException -> "The server sent data this version of the app cannot read."
    else -> e.message ?: "Something went wrong. Try again."
}
