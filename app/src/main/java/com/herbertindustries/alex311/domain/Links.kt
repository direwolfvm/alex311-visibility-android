package com.herbertindustries.alex311.domain

import java.net.URI
import java.net.URLDecoder

object Links {
    const val scheme = "com.herbertindustries.alex311-reborn"
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
        data object Mine: Destination
        data object Report: Destination
    }
    fun destination(raw: String): Destination? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != scheme && !(uri.scheme == "https" && uri.host == "alex311visibility.me")) return null
        val path = (if (uri.scheme == scheme) listOfNotNull(uri.host) else emptyList()) + uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return when {
            path.size == 2 && path[0] == "r" && path[1].matches(Regex("\\d{2}-\\d{8}")) -> Destination.Case(path[1])
            path == listOf("submit", "my") || path == listOf("my") -> Destination.Mine
            path == listOf("report") || path == listOf("submit") -> Destination.Report
            else -> null
        }
    }
}
