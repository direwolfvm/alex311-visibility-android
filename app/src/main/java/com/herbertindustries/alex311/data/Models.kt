package com.herbertindustries.alex311.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Wire names intentionally match the shared iOS/web contract.
@Serializable data class Category(val service_name: String, val n: Int = 0)
@Serializable data class Meta(val categories: List<Category> = emptyList(), val min_date: String? = null, val max_date: String? = null, val total: Int = 0, val open: Int = 0)
@Serializable data class Request(
    val service_request_id: String, val status: String? = null, val service_name: String? = null,
    val lat: Double? = null, val long: Double? = null, val address: String? = null,
    val requested_datetime: String? = null, val last_updated_datetime: String? = null,
    val closed_datetime: String? = null, val description: String? = null,
    val media_count: Int? = null, val media_ids: List<String> = emptyList(), val report_url: String? = null,
    val status_notes: String? = null, val closure_details: String? = null, val priority: String? = null,
    val primary_service_department: String? = null, val agency_responsible: String? = null,
    val expected_datetime: String? = null, val origin: String? = null,
    val parent_service_request_id: String? = null, val duplicate_parent_service_request_id: String? = null,
    val media: List<Media> = emptyList(), val related: List<Related> = emptyList(),
    val resident_scores: List<ResidentScore> = emptyList(), val relation: String? = null,
    val in_mirror: Boolean? = null, val is_new: Boolean = false
)
@Serializable data class RequestPage(val total: Int, val rows: List<Request>)
@Serializable data class Media(val media_id: String, val file_name: String? = null, val mime_type: String? = null, val stored_mime: String? = null, val stored: Boolean = false)
@Serializable data class Related(val service_request_id: String, val service_name: String? = null, val status: String? = null, val relation: String = "related")
@Serializable data class ResidentScore(val score: Int? = null, val relation: String? = null, val status_at_rating: String? = null)
@Serializable data class User(val user_id: String, val email: String? = null, val display_name: String? = null, val label: String? = null)
@Serializable data class WhoAmI(val user: User? = null)
@Serializable data class FirebaseConfig(val apiKey: String, val projectId: String? = null, val tenantId: String? = null)
@Serializable data class FirebaseEnvelope(val config: FirebaseConfig? = null)
@Serializable data class MyLinks(val links: List<Request> = emptyList())
@Serializable data class Watch(val watch_id: Int, val kind: String, val label: String? = null, val spec: JsonElement? = null)
@Serializable data class WatchList(val watches: List<Watch> = emptyList())
@Serializable data class Feed(val items: List<Request> = emptyList(), val watches: List<Watch> = emptyList(), val total: Int? = null)
@Serializable data class Feedback(val score: Int? = null, val note: String? = null, val share_score: Boolean? = null)
@Serializable data class FeedbackEnvelope(val feedback: Feedback? = null)
@Serializable data class Digest(val status: String? = null, val email: String? = null, val account_email: String? = null, val available: Boolean? = null)
@Serializable data class Registry(val version: String, val schema: Int, val services: List<Service>)
@Serializable data class Coverage(val mined_records: Int = 0, val walked: Boolean? = null)
@Serializable data class Department(val name: String, val phone: String? = null, val email: String? = null, val web_link: String? = null)
@Serializable data class Service(
    val service_code: String, val service_name: String, val description: String? = null,
    val keywords: String? = null, val groups: List<String> = emptyList(), val coverage: Coverage? = null,
    val departments: List<Department> = emptyList(), val banners: List<String> = emptyList(),
    val questions: List<Question> = emptyList()
)
@Serializable data class Question(
    val order: Int, val code: String? = null, val text: String, val kind: String? = null,
    val datatype: String? = null, val required: Boolean = false, val options: List<Option> = emptyList(),
    val source: String? = null, val conditional: Boolean = false, val revealed_by: List<Reveal> = emptyList(), val note: String? = null
) {
    val key: String get() = code?.takeIf { it.isNotEmpty() } ?: "q$order"
    val input: Input get() = when {
        kind in listOf("checkbox", "multiselect") || datatype == "multivaluelist" -> Input.MULTIPLE
        options.isNotEmpty() -> Input.SINGLE
        kind == "composite" || datatype?.startsWith("date") == true -> Input.DATE_TIME
        else -> Input.TEXT
    }
    val choosableOptions: List<Option> get() = if (kind == null) options else options.filter { it.rendered != false }
}
enum class Input { SINGLE, MULTIPLE, DATE_TIME, TEXT }
@Serializable data class Option(val value: String, val rendered: Boolean? = null, val rule: Rule? = null)
@Serializable data class Rule(val type: String, val message: String? = null)
@Serializable data class Reveal(val question: String, val option: String)
@Serializable data class ValidationItem(val question: String, val message: String, val option: String? = null)
@Serializable data class Validation(val ok: Boolean, val errors: List<ValidationItem> = emptyList(), val blocking: List<ValidationItem> = emptyList(), val advisories: List<ValidationItem> = emptyList(), val info: List<ValidationItem> = emptyList())
@Serializable data class Candidate(val address: String, val lat: Double, val long: Double)
@Serializable data class Geocode(val candidates: List<Candidate> = emptyList(), val suggestion: Candidate? = null)
@Serializable data class Reverse(val nearest: Candidate? = null)
@Serializable data class NearbyItem(val service_request_id: String, val service_name: String? = null, val address: String? = null, val status: String? = null, val meters: Int = 0)
@Serializable data class NearbyResult(val nearby: List<NearbyItem> = emptyList(), val open_count: Int = 0)
@Serializable data class Finding(val rule: String, val outcome: String, val message: String)
@Serializable data class Precheck(val attempt_id: Int, val outcome: String, val may_proceed: Boolean, val findings: List<Finding> = emptyList(), val summary: String? = null, val cooldown_until: String? = null)
@Serializable data class Attempt(val attempt_id: Int, val submit_state: String, val city_case_number: String? = null, val error: String? = null, val message: String? = null, val photos: Int? = null, val photo_note: String? = null) {
    val isFinal: Boolean get() = submit_state in listOf("filed", "failed", "cancelled")
}
@Serializable data class Contact(val first_name: String = "", val last_name: String = "", val email: String = "", val phone: String = "") {
    val complete: Boolean get() = listOf(first_name, last_name, email, phone).all { it.isNotBlank() }
}
@Serializable data class Analytics(
    val summary: Summary, val ttc_histogram: List<Bucket> = emptyList(), val by_category: List<CategoryStats> = emptyList(),
    val weekly: List<Week> = emptyList(), val weekday: List<Weekday> = emptyList()
)
@Serializable data class Summary(val total: Int, val open: Int, val closed: Int, val median_ttc: Double? = null, val mean_ttc: Double? = null, val closed_within_30d: Int? = null, val avg_open_age: Double? = null, val oldest_open_age: Double? = null)
@Serializable data class Bucket(val bucket: Int, val n: Int)
@Serializable data class CategoryStats(val service_name: String, val n: Int, val open_n: Int = 0, val median_ttc: Double? = null)
@Serializable data class Week(val week: String, val opened: Double, val closed: Double)
@Serializable data class Weekday(val dow: Int, val n: Int)
data class MapPoint(val id: String, val lat: Double, val lng: Double, val status: String?, val category: String = "", val age: Int = -1)
