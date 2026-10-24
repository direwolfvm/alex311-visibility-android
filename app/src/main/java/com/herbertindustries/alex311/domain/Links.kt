package com.herbertindustries.alex311.domain

import java.net.URI
import java.net.URLDecoder

object Links {
    const val scheme = "com.herbertindustries.alex311-reborn"
    const val origin = "https://alex311-reborn.com"
    private val webHosts = setOf("alex311-reborn.com", "alex311visibility.me")
    private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair.size == 2) runCatching { URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8") }.getOrNull() else null
    }.toMap()
    fun oobCode(raw: String, depth: Int = 0): String? {
        if (depth >= 4) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val q = query(uri)
        if (q["mode"] == "signIn" && !q["oobCode"].isNullOrBlank()) return q["oobCode"]
        return q.values.firstNotNullOfOrNull { if (it.startsWith("http") || it.startsWith("$scheme:")) oobCode(it, depth + 1) else null }
    }
    fun pastedCode(text: String): String? = oobCode(text.trim()) ?: Regex("(?:https?://|$scheme://)[^\\s<>\"]+").findAll(text).firstNotNullOfOrNull { oobCode(it.value) }
    sealed interface Destination {
        data class Case(val id: String): Destination
        data class SignIn(val code: String?): Destination
        data object Mine: Destination
        data object Report: Destination
    }
    fun destination(raw: String): Destination? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val custom = uri.scheme == scheme
        val path = when {
            custom && uri.host != null -> "/${uri.host}${uri.path.orEmpty()}"
            uri.scheme == "https" && uri.host in webHosts -> uri.path.orEmpty()
            else -> return null
        }
        return when {
            path.matches(Regex("/r/\\d{2}-\\d{8}")) -> Destination.Case(path.substringAfterLast('/'))
            path == "/submit/login" || custom && path == "/signin" -> Destination.SignIn(oobCode(raw))
            path == "/submit/my" || custom && path == "/my" -> Destination.Mine
            path == "/submit" || custom && path == "/report" -> Destination.Report
            else -> null
        }
    }
}
