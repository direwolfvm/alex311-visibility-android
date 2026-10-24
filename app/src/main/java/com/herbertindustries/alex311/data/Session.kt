package com.herbertindustries.alex311.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.herbertindustries.alex311.BuildConfig
import com.herbertindustries.alex311.domain.Links
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import android.util.Base64

class Session(private val context: Context, val api: Api, private val secure: SecureStore) {
    val user = MutableStateFlow<User?>(null)
    val signedIn = MutableStateFlow(secure.read("session") != null)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var pendingEmail: String
        get() = prefs.getString("pendingEmail", "").orEmpty()
        set(value) { prefs.edit().putString("pendingEmail", value).apply() }
    var heldCode: String? = null
    init {
        api.sessionToken = secure.read("session")
        api.onUnauthorized = { forget() }
    }
    suspend fun restore() {
        if (!signedIn.value) return
        val reply = api.get<WhoAmI>("submit/api/whoami")
        user.value = reply.user
        if (reply.user == null) forget()
    }
    suspend fun sendLink(raw: String) {
        val email = raw.trim().lowercase()
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Enter a valid email address." }
        val retryAt = prefs.getLong("emailRetryAt.$email", 0)
        require(System.currentTimeMillis() >= retryAt) { "Wait a minute before requesting another email. You can still use the newest link or code." }
        pendingEmail = email
        try {
            api.fire("POST", "submit/api/email-link", obj("email" to email))
        } catch (e: ApiException) {
            if (e.status == 429) prefs.edit().putLong("emailRetryAt.$email", System.currentTimeMillis() + (e.retryAfter ?: 60) * 1000).apply()
            if (e.status != 503) throw e
            firebase("sendOobCode", obj("requestType" to "EMAIL_SIGNIN", "email" to email, "continueUrl" to api.url("submit/login").toString(), "canHandleCodeInApp" to true, "androidPackageName" to BuildConfig.APPLICATION_ID))
        }
        prefs.edit().putLong("emailRetryAt.$email", System.currentTimeMillis() + 60000).apply()
    }
    suspend fun complete(email: String, text: String) {
        val input = text.trim()
        val oob = if (input.matches(Regex("\\d{6}"))) {
            val reply = api.call<JsonObject>("POST", "submit/api/email-code", obj("email" to email.trim().lowercase(), "code" to input))
            reply["oobCode"]!!.jsonPrimitive.content
        } else Links.pastedCode(input) ?: throw IllegalArgumentException("Paste the sign-in link or enter the six-digit code from the newest email.")
        completeOob(email, oob)
    }
    suspend fun completeOob(email: String, code: String) {
        require(email.isNotBlank()) { "Enter the email address this sign-in link was sent to." }
        val reply = firebase("signInWithEmailLink", obj("email" to email.trim().lowercase(), "oobCode" to code))
        finish(reply["idToken"]!!.jsonPrimitive.content)
        heldCode = null
        pendingEmail = ""
    }
    suspend fun google(activity: Context) {
        require(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google sign-in is not configured in this build. Use email sign-in." }
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (e: NoCredentialException) { throw IOException("No Google account is available. Add one to Android, or use email sign-in.") }
        catch (e: GetCredentialCancellationException) { throw IOException("Google sign-in was cancelled.") }
        require(result.credential is CustomCredential && result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Google returned an unsupported sign-in credential." }
        val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        val post = okhttp3.FormBody.Builder().add("id_token", token).add("providerId", "google.com").build()
        val buffer = okio.Buffer(); post.writeTo(buffer)
        val reply = firebase("signInWithIdp", obj("postBody" to buffer.readUtf8(), "requestUri" to "http://localhost", "returnSecureToken" to true, "returnIdpCredential" to true))
        finish(reply["idToken"]!!.jsonPrimitive.content)
    }
    private suspend fun finish(token: String) {
        val cookie = api.createSession(token)
        secure.write("session", cookie)
        api.sessionToken = cookie
        signedIn.value = true
        restore()
    }
    private suspend fun firebase(method: String, body: JsonObject): JsonObject {
        val config = api.get<FirebaseEnvelope>("submit/api/firebase-config").config ?: throw IOException("Sign-in is not configured on this server.")
        return withContext(Dispatchers.IO) {
            val payload = JsonObject(body + listOfNotNull(config.tenantId?.let { "tenantId" to JsonPrimitive(it) }).toMap())
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:$method".toHttpUrl().newBuilder().addQueryParameter("key", config.apiKey).build()
            val req = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            OkHttpClient().newCall(req).execute().use { response ->
                val json = WireJson.parseToJsonElement(response.body!!.string()).jsonObject
                if (!response.isSuccessful) {
                    val code = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content.orEmpty().substringBefore(' ')
                    throw IOException(when (code) {
                        "INVALID_OOB_CODE", "EXPIRED_OOB_CODE" -> "That link was used or has expired. Ask for a new one."
                        "INVALID_EMAIL" -> "Use the email address this link was sent to."
                        "USER_DISABLED" -> "This account cannot sign in."
                        else -> "Sign-in could not be verified ($code). Try again."
                    })
                }
                json
            }
        }
    }
    suspend fun signOut(everywhere: Boolean = false) {
        try { api.fire("POST", if (everywhere) "submit/api/logout-all" else "submit/logout") }
        finally { forget() }
    }
    suspend fun deleteAccount() { api.fire("DELETE", "submit/api/account"); forget() }
    fun forget() {
        api.sessionToken = null; user.value = null; signedIn.value = false
        secure.clearAccount()
        prefs.edit().remove("statusSnapshot").remove("seenStatuses").apply()
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("status-check")
    }
}
