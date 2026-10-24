package com.herbertindustries.alex311.ui

import androidx.compose.runtime.*
import com.herbertindustries.alex311.Alex311Application
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

@Serializable data class Draft(
    val serviceCode: String? = null, val lat: Double? = null, val lng: Double? = null,
    val address: String = "", val cityAddress: String? = null, val answers: Map<String, JsonElement> = emptyMap(),
    val description: String = "", val photos: List<String> = emptyList(), val uploaded: Map<String, Int> = emptyMap(),
    val step: Int = 0, val pendingAttempt: Int? = null
) { val coordinate get() = lat?.let { a -> lng?.let { b -> Coordinate(a, b) } } }
class ReportState(private val app: Alex311Application, private val scope: CoroutineScope, private val api: Api = app.api) {
    private val store = RegistryStore(app, api)
    var draft by mutableStateOf(runCatching { app.secure.read("draft")?.let { WireJson.decodeFromString<Draft>(it) } }.getOrNull() ?: Draft())
        private set
    var services by mutableStateOf(emptyList<Service>())
    var search by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var candidates by mutableStateOf(emptyList<Candidate>())
    var nearby by mutableStateOf(emptyList<NearbyItem>())
    var validation by mutableStateOf<Validation?>(null)
    var precheck by mutableStateOf<Precheck?>(null)
    var attempt by mutableStateOf<Attempt?>(null)
    var pollNote by mutableStateOf<String?>(null)
    var contact by mutableStateOf(runCatching { app.secure.read("contact")?.let { WireJson.decodeFromString<Contact>(it) } }.getOrNull() ?: Contact())
    var rememberContact by mutableStateOf(app.secure.read("contact") != null)
    var acknowledged by mutableStateOf(false)
    private var actionJob: Job? = null
    private var pollJob: Job? = null
    private var searchJob: Job? = null
    val service get() = services.firstOrNull { it.service_code == draft.serviceCode }
    val logic get() = FormLogic(service?.questions ?: emptyList())
    val matches get() = services.filter { "${it.service_name} ${it.keywords.orEmpty()} ${it.description.orEmpty()}".contains(search.trim(), true) }.sortedByDescending { it.coverage?.mined_records ?: 0 }
    val readyLocation get() = draft.coordinate?.let { Geo.inCity(it) } == true
    init { draft.pendingAttempt?.let { poll(it) } }
    private fun save(value: Draft) { draft = value; app.secure.write("draft", WireJson.encodeToString(value)) }
    fun edit(transform: (Draft) -> Draft) {
        save(transform(draft)); validation = null; precheck = null; acknowledged = false; error = null
    }
    fun step(value: Int) { save(draft.copy(step = value)) }
    fun load() = scope.launch {
        if (loading) return@launch
        loading = true; error = null
        try { services = store.load().services }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) }
        finally { loading = false }
    }
    fun choose(value: Service) {
        val changed = value.service_code != draft.serviceCode
        edit { it.copy(serviceCode = value.service_code, answers = if (changed) FormLogic(value.questions).defaults() else it.answers, step = 1) }
        refreshNearby()
    }
    fun seed(name: String?, coordinate: Coordinate?, address: String?) {
        if (draft.pendingAttempt != null || busy) return
        if (coordinate != null) edit { it.copy(lat = coordinate.lat, lng = coordinate.lng, address = address.orEmpty(), cityAddress = address, step = if (it.serviceCode != null) 1 else 0) }
        if (name != null) scope.launch {
            if (services.isEmpty()) { try { services = store.load().services } catch (e: Exception) { error = friendly(e); return@launch } }
            services.firstOrNull { it.service_name.equals(name, true) }?.let { choose(it) }
        }
    }
    fun address(value: String) {
        edit { it.copy(address = value, cityAddress = null, lat = null, lng = null) }
        candidates = emptyList(); nearby = emptyList()
        searchJob?.cancel()
        if (value.trim().length < 3) return
        searchJob = scope.launch {
            delay(450)
            try {
                val result = api.get<Geocode>("submit/api/geocode", listOf("q" to value.trim(), "limit" to "6"))
                if (draft.address == value) candidates = (listOfNotNull(result.suggestion) + result.candidates).distinctBy { it.address }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) }
        }
    }
    fun pick(candidate: Candidate) {
        searchJob?.cancel(); candidates = emptyList()
        edit { it.copy(address = candidate.address, cityAddress = candidate.address, lat = candidate.lat, lng = candidate.long) }
        refreshNearby()
    }
    fun pin(coordinate: Coordinate) = work {
        searchJob?.cancel(); candidates = emptyList()
        edit { it.copy(lat = coordinate.lat, lng = coordinate.lng, address = "", cityAddress = null) }
        val result = api.get<Reverse>("submit/api/reverse", listOf("lat" to "${coordinate.lat}", "long" to "${coordinate.lng}"))
        result.nearest?.let { pick(it) }
        refreshNearby()
    }
    fun refreshNearby() = scope.launch {
        val c = draft.coordinate ?: return@launch
        val name = service?.service_name ?: return@launch
        try {
            val result = api.get<NearbyResult>("submit/api/nearby", listOf("lat" to "${c.lat}", "long" to "${c.lng}", "category" to name))
            if (draft.coordinate == c && service?.service_name == name) nearby = result.nearby
        } catch (e: CancellationException) { throw e } catch (_: Exception) { nearby = emptyList() }
    }
    fun answer(key: String, value: JsonElement) { edit { it.copy(answers = it.answers + (key to value)) } }
    fun addPhotos(uris: List<android.net.Uri>) = work {
        for (uri in uris.take((3 - draft.photos.size).coerceAtLeast(0))) {
            val data = Photos.prepare(app, uri)
            val name = "report-${UUID.randomUUID()}.jpg"
            withContext(Dispatchers.IO) { File(app.filesDir, name).writeBytes(data) }
            edit { it.copy(photos = it.photos + name) }
        }
    }
    fun photoFile(name: String) = File(app.filesDir, name)
    fun removePhoto(name: String) { edit { it.copy(photos = it.photos - name, uploaded = it.uploaded - name) }; photoFile(name).delete() }
    fun check() = work {
        require(readyLocation) { "Choose a location in Alexandria." }
        require(logic.check(draft.answers).canContinue) { "Complete the required questions before checking." }
        val s = service ?: error("Choose a request type.")
        val checkedDraft = draft
        val answers = logic.payload(draft.answers)
        precheck = null
        val checkedValidation: Validation = api.call("POST", "submit/api/validate", obj("service_code" to s.service_code, "answers" to answers))
        if (draft != checkedDraft) return@work
        validation = checkedValidation
        if (!checkedValidation.ok) return@work
        val checkedPrecheck: Precheck = api.call("POST", "submit/api/precheck", obj("service_code" to s.service_code, "service_name" to s.service_name,
            "address" to draft.address.takeIf { it.isNotBlank() }, "city_address" to draft.cityAddress,
            "lat" to draft.lat, "long" to draft.lng, "description" to draft.description.trim(), "answers" to answers))
        if (draft == checkedDraft) precheck = checkedPrecheck
    }
    val canSend get() = !busy && contact.complete && validation?.ok == true && precheck?.outcome != "block" && precheck != null && (precheck?.outcome != "review" || acknowledged)
    fun send() = work {
        require(contact.complete) { "Complete your contact details." }
        val check = precheck ?: error("Check this report before sending.")
        require(validation?.ok == true && check.outcome != "block" && (check.outcome != "review" || acknowledged)) { "Resolve the report checks before sending." }
        app.secure.write("contact", if (rememberContact) WireJson.encodeToString(contact) else null)
        draft.photos.forEachIndexed { index, name ->
            if (draft.uploaded[name] != check.attempt_id) {
                val data = withContext(Dispatchers.IO) { photoFile(name).readBytes() }
                api.raw("POST", "submit/api/attempt/${check.attempt_id}/photos", query = listOf("name" to "photo-${index + 1}.jpg"), bytes = data)
                save(draft.copy(uploaded = draft.uploaded + (name to check.attempt_id)))
            }
        }
        // Persist before queueing: a lost connection/process must resume this attempt, never create a second report.
        save(draft.copy(pendingAttempt = check.attempt_id, step = 4))
        try {
            val body = obj("attempt_id" to check.attempt_id, "first_name" to contact.first_name.trim(), "last_name" to contact.last_name.trim(), "email" to contact.email.trim(), "phone" to contact.phone.trim(), "acknowledged" to acknowledged)
            attempt = api.call("POST", "submit/api/queue", body)
        } finally { poll(check.attempt_id) }
    }
    fun resume() { draft.pendingAttempt?.let { poll(it) } }
    private fun poll(id: Int) {
        pollJob?.cancel()
        pollJob = scope.launch {
            pollNote = null
            val deadline = System.currentTimeMillis() + 300000
            while (isActive && System.currentTimeMillis() < deadline) {
                try {
                    attempt = api.get("submit/api/status/$id")
                    if (attempt!!.isFinal) {
                        if (attempt!!.submit_state == "filed") draft.photos.forEach { photoFile(it).delete() }
                        return@launch
                    }
                    if (attempt!!.submit_state == "prepared") {
                        pollNote = "This attempt has not been queued. Return to review, check it again, and send when ready."
                        return@launch
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { pollNote = friendly(e) }
                delay(2500)
            }
            pollNote = "Still working. Check My Issues for the City's case number, or refresh this status."
        }
    }
    fun returnToReview() { pollJob?.cancel(); save(draft.copy(pendingAttempt = null, step = 3)); precheck = null; validation = null; attempt = null }
    fun reset() { pollJob?.cancel(); draft.photos.forEach { photoFile(it).delete() }; save(Draft()); precheck = null; validation = null; attempt = null; error = null; nearby = emptyList() }
    fun clearPrivateState() { actionJob?.cancel(); searchJob?.cancel(); reset(); contact = Contact(); rememberContact = false }
    fun summary(): String = buildString {
        appendLine(service?.service_name ?: "Request"); appendLine("Location: ${draft.cityAddress ?: draft.address}")
        draft.coordinate?.let { appendLine("Coordinates: ${it.lat}, ${it.lng}") }
        logic.visible(draft.answers).forEach { q -> draft.answers[q.key]?.let { appendLine("${q.text}: $it") } }
        appendLine(draft.description)
    }
    private fun work(block: suspend () -> Unit): Job = scope.launch {
        if (busy) return@launch
        busy = true; error = null
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) }
        finally { busy = false }
    }.also { actionJob = it }
}
