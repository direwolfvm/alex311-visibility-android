package com.herbertindustries.alex311

import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FormLogicTest {
    @Serializable data class Fixture(val services: List<Service>, val cases: List<Case>)
    @Serializable data class Case(val service: String, val answers: Map<String, JsonElement>, val errors: List<String>, val blocking: List<String>, val advisories: List<String>, val info: List<String>, val ok: Boolean)
    private fun fixture() = WireJson.decodeFromString<Fixture>(javaClass.getResource("/registry-parity.json")!!.readText())
    @Test fun `matches all server registry validation fixtures`() {
        val fx = fixture()
        assertEquals(30, fx.services.size)
        assertEquals(180, fx.cases.size)
        val services = fx.services.associateBy { it.service_code }
        for ((index, case) in fx.cases.withIndex()) {
            val logic = FormLogic(services.getValue(case.service).questions)
            val check = logic.check(case.answers)
            val label = "fixture $index service ${case.service}: ${case.answers}"
            assertEquals("$label required", case.errors, check.missing.map { it.key }.sorted())
            assertEquals("$label blocking", case.blocking, check.blocking.map { "${it.question}=${it.option}" }.sorted())
            assertEquals("$label advisories", case.advisories, check.advisories.map { "${it.question}=${it.option}" }.sorted())
            assertEquals("$label info", case.info, check.info.map { "${it.question}=${it.option}" }.sorted())
            assertEquals("$label continuation", case.ok, check.canContinue)
        }
    }
    @Test fun `payloads have the same keys and values as server fixtures`() {
        val fx = fixture(); val services = fx.services.associateBy { it.service_code }
        fx.cases.forEach { case ->
            val payload = FormLogic(services.getValue(case.service).questions).payload(case.answers)
            assertEquals(case.answers.keys, payload.keys)
            case.answers.forEach { (key, expected) ->
                if (expected is JsonArray) assertEquals(expected.toSet(), payload[key]!!.jsonArray.toSet())
                else assertEquals(expected, payload[key])
            }
        }
    }
    @Test fun `conditional and data-only answers are excluded`() {
        val type = Question(1, "type", "Type", options = listOf(Option("A"), Option("B")))
        val hidden = Question(2, "more", "More", conditional = true, revealed_by = listOf(Reveal("type", "A")))
        val data = Question(3, "data", "Data", source = "data-only")
        val logic = FormLogic(listOf(type, hidden, data))
        assertEquals(setOf("type"), logic.payload(mapOf("type" to JsonPrimitive("B"), "more" to JsonPrimitive("stale"), "data" to JsonPrimitive("never send"))).keys)
        assertEquals(listOf("type", "more"), logic.visible(mapOf("type" to JsonPrimitive("A"))).map { it.key })
    }
    @Test fun `hard stops always explain why even without a server message`() {
        val q = Question(1, "who", "Who", options = listOf(Option("Landlord", rule = Rule("hard_stop"))))
        val check = FormLogic(listOf(q)).check(mapOf("who" to JsonPrimitive("Landlord")))
        assertFalse(check.canContinue); assertTrue(check.blocking.single().text.contains("without giving a reason"))
    }
    @Test fun `date answers always use Alexandria time including DST`() {
        assertEquals(obj("date" to "2026-09-30", "time" to "21:30"), FormLogic.dateAnswer(Instant.parse("2026-10-01T01:30:00Z")))
        assertEquals(obj("date" to "2026-12-31", "time" to "20:30"), FormLogic.dateAnswer(Instant.parse("2027-01-01T01:30:00Z")))
    }
    @Test fun `full registry sample loads all questions and departments`() {
        val registry = WireJson.decodeFromString<Registry>(javaClass.getResource("/registry-full-sample.json")!!.readText())
        assertEquals(1, registry.schema); assertEquals(2, registry.services.size)
        assertTrue(registry.services.first { it.service_code == "TESCONTN" }.departments.isNotEmpty())
        assertTrue(registry.services.any { it.questions.isEmpty() })
    }
}
