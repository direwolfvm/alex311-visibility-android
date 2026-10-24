package com.herbertindustries.alex311.domain

import java.util.Locale
import kotlin.math.*

data class Coordinate(val lat: Double, val lng: Double)
object Geo {
    val city = Coordinate(38.8048, -77.0469)
    fun inCity(c: Coordinate) = c.lat in 38.760..38.860 && c.lng in -77.170..-77.020
    fun circle(c: Coordinate, meters: Double, sides: Int = 24): List<Coordinate> = (0 until sides).map {
        val a = it.toDouble() / sides * 2 * PI
        Coordinate(c.lat + meters * sin(a) / 111320, c.lng + meters * cos(a) / (111320 * cos(c.lat * PI / 180)))
    }
    fun distance(a: Coordinate, b: Coordinate): Double {
        val lat = Math.toRadians(b.lat - a.lat); val lng = Math.toRadians(b.lng - a.lng)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(lng / 2).pow(2)
        return 6371000 * 2 * atan2(sqrt(h), sqrt(1 - h.coerceIn(0.0, 1.0)))
    }
    fun describe(meters: Double): String = if (meters * 3.28084 < 1000) "${(meters * 3.28084 / 10).roundToInt() * 10} ft" else String.format(Locale.US, "%.1f mi", meters / 1609.34)
}
data class RequestFilter(val start: String? = null, val end: String? = null, val categories: List<String> = emptyList(), val status: String? = null, val query: String = "", val polygon: List<Coordinate>? = null) {
    fun parameters(): List<Pair<String, String>> = buildList {
        start?.let { add("start" to it) }; end?.let { add("end" to it) }
        categories.forEach { add("category" to it) }; status?.let { add("status" to it) }
        if (query.isNotBlank()) add("q" to query.trim())
        polygon?.let { add("polygon" to it.joinToString(";") { c -> String.format(Locale.US, "%.6f,%.6f", c.lng, c.lat) }) }
    }
}
