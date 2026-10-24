package com.herbertindustries.alex311.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RegistryStore(context: Context, private val api: Api) {
    private val origin = java.security.MessageDigest.getInstance("SHA-256").digest(api.baseUrl.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    private val file = File(context.filesDir, "registry-$origin.json")
    private val tag = File(context.filesDir, "registry-$origin.etag")
    suspend fun load(): Registry = withContext(Dispatchers.IO) {
        val cached = runCatching { WireJson.decodeFromString<Registry>(file.readText()).also { require(it.schema == 1) } }.getOrNull()
        try {
            val fresh = api.registry(if (cached != null) tag.takeIf { it.exists() }?.readText() else null)
            if (fresh != null) {
                val temporary = File(file.parentFile, "registry-$origin.tmp")
                temporary.writeText(WireJson.encodeToString(Registry.serializer(), fresh.first))
                check(temporary.renameTo(file)) { "Could not save request types." }
                tag.writeText(fresh.second)
                fresh.first
            } else requireNotNull(cached) { "No saved request types. Connect and try again." }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is ApiException && e.status == 401) throw e
            cached ?: throw e
        }
    }
}
