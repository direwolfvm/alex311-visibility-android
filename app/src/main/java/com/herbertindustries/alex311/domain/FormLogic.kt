package com.herbertindustries.alex311.domain

import com.herbertindustries.alex311.data.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FormLogic(val questions: List<Question>) {
    data class Message(val question: String, val option: String, val text: String)
    data class Check(val missing: List<Question>, val blocking: List<Message>, val advisories: List<Message>, val info: List<Message>) {
        val canContinue get() = missing.isEmpty() && blocking.isEmpty()
    }
    fun selected(value: JsonElement?): List<String> = when (value) {
        is JsonPrimitive -> if (value.isString) listOf(value.content) else emptyList()
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }
    private fun empty(value: JsonElement?): Boolean = value == null || value == JsonNull ||
        (value is JsonPrimitive && value.content.isBlank()) || (value is JsonArray && value.isEmpty())
    fun visible(answers: Map<String, JsonElement>): List<Question> = questions.filter { q ->
        q.source != "data-only" && (!q.conditional || q.revealed_by.any { it.option in selected(answers[it.question]) })
    }.sortedBy { it.order }
    fun check(answers: Map<String, JsonElement>): Check {
        val missing = mutableListOf<Question>()
        val blocking = mutableListOf<Message>()
        val advisories = mutableListOf<Message>()
        val info = mutableListOf<Message>()
        visible(answers).forEach { q ->
            if (empty(answers[q.key])) {
                if (q.required) missing += q
            } else selected(answers[q.key]).forEach { value ->
                q.options.firstOrNull { it.value == value }?.rule?.let { rule ->
                    val text = rule.message?.trim().orEmpty()
                    val message = Message(q.key, value, text.ifEmpty { "The City's form rejects this answer without giving a reason." })
                    when (rule.type) {
                        "hard_stop" -> blocking += message
                        "advisory" -> if (text.isNotEmpty()) advisories += message
                        "info" -> if (text.isNotEmpty()) info += message
                    }
                }
            }
        }
        return Check(missing, blocking, advisories, info)
    }
    fun payload(answers: Map<String, JsonElement>): JsonObject = buildJsonObject {
        visible(answers).forEach { q ->
            val value = answers[q.key]
            if (!empty(value)) put(q.key, if (q.input == Input.TEXT) JsonPrimitive((value as JsonPrimitive).content.trim()) else value!!)
        }
    }
    fun defaults(now: Instant = Instant.now()): Map<String, JsonElement> = questions.filter { it.source != "data-only" && it.input == Input.DATE_TIME }.associate { it.key to dateAnswer(now) }
    companion object {
        fun dateAnswer(instant: Instant): JsonObject {
            val local = instant.atZone(ZoneId.of("America/New_York"))
            return buildJsonObject {
                put("date", local.toLocalDate().toString())
                put("time", local.format(DateTimeFormatter.ofPattern("HH:mm")))
            }
        }
    }
}
