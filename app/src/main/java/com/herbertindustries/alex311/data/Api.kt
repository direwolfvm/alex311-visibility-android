package com.herbertindustries.alex311.data

import com.herbertindustries.alex311.domain.RequestFilter
import com.herbertindustries.alex311.domain.Links
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

val WireJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
class ApiException(val status: Int, message: String, val retryAfter: Long? = null): IOException(message)
class Api(
    val baseUrl: String = PRODUCTION,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).followRedirects(false).build()
) {
    @Volatile var sessionToken: String? = null
    var onUnauthorized: (() -> Unit)? = null
    fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl = baseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
        query.forEach { (k, v) -> addQueryParameter(k, v) }
    }.build()
    data class Reply(val text: String, val headers: Headers, val status: Int)
    suspend fun raw(method: String, path: String, body: JsonElement? = null, query: List<Pair<String, String>> = emptyList(), bytes: ByteArray? = null, etag: String? = null): Reply = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder().url(url(path, query)).header("Accept", "application/json")
        val requestToken = if (path.trimStart('/').startsWith("submit/")) sessionToken else null
        if (requestToken != null) request.header("Cookie", "alex311_portal=$requestToken")
        etag?.let { request.header("If-None-Match", it) }
        val payload = bytes?.toRequestBody("image/jpeg".toMediaType()) ?: body?.toString()?.toRequestBody("application/json".toMediaType())
        request.method(method, payload ?: if (method in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody(null) else null)
        client.newCall(request.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (requestToken != null && sessionToken != requestToken) throw IOException("This request belongs to a signed-out session.")
            if (response.code == 401) {
                if (sessionToken != null) onUnauthorized?.invoke()
                throw ApiException(401, "Please sign in again.")
            }
            if (!response.isSuccessful && response.code != 304) {
                val detail = runCatching { WireJson.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw ApiException(response.code, detail ?: if (response.code == 429) "Too many tries. Wait a moment and try again." else "The server answered ${response.code}.", response.header("Retry-After")?.toLongOrNull())
            }
            Reply(text, response.headers, response.code)
        }
    }
    suspend inline fun <reified T> get(path: String, query: List<Pair<String, String>> = emptyList()): T = WireJson.decodeFromString(raw("GET", path, query = query).text)
    suspend inline fun <reified T> call(method: String, path: String, body: JsonElement? = null): T = WireJson.decodeFromString(raw(method, path, body).text)
    suspend fun fire(method: String, path: String, body: JsonElement? = null) { raw(method, path, body) }
    suspend fun meta(): Meta = get("api/meta")
    suspend fun requests(filter: RequestFilter, sort: String = "requested", offset: Int = 0, limit: Int = 100): RequestPage = get("api/requests", filter.parameters() + listOf("sort" to sort, "dir" to if (sort in listOf("category", "address")) "asc" else "desc", "limit" to "$limit", "offset" to "$offset"))
    suspend fun request(id: String): Request = get("api/request/$id")
    suspend fun points(filter: RequestFilter): List<MapPoint> {
        val response: JsonObject = get("api/points", filter.parameters())
        val categories = (response["categories"] as? JsonArray ?: throw SerializationException("Map points are missing categories.")).map { it.jsonPrimitive.content }
        return (response["points"] as? JsonArray ?: throw SerializationException("Map points are missing.")).map { row ->
            val a = row.jsonArray
            MapPoint(a[0].jsonPrimitive.content, a[1].jsonPrimitive.double, a[2].jsonPrimitive.double, a[3].jsonPrimitive.contentOrNull, categories.getOrElse(a[4].jsonPrimitive.int) { "Unknown" }, a[5].jsonPrimitive.int)
        }
    }
    suspend fun analytics(filter: RequestFilter): Analytics = get("api/analytics", filter.parameters())
    fun mediaUrl(id: String, width: Int? = null): String = url("api/media/$id", width?.let { listOf("w" to "$it") } ?: emptyList()).toString()
    suspend fun createSession(idToken: String): String {
        val reply = raw("POST", "submit/api/session", obj("id_token" to idToken))
        return reply.headers.values("Set-Cookie").mapNotNull { Cookie.parse(url("submit/api/session"), it) }.firstOrNull { it.name == "alex311_portal" }?.value
            ?: throw IOException("The server did not start a session.")
    }
    suspend fun registry(etag: String?): Pair<Registry, String>? {
        val reply = raw("GET", "submit/api/registry/full", etag = etag)
        if (reply.status == 304) return null
        val registry = WireJson.decodeFromString<Registry>(reply.text)
        require(registry.schema == 1) { "This request registry needs a newer version of the app." }
        return registry to (reply.headers["ETag"] ?: "\"${registry.version}\"")
    }
    companion object { const val PRODUCTION = Links.origin }
}
fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) -> put(key, when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }) }
}
