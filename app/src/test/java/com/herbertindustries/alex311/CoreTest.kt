package com.herbertindustries.alex311

import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder
import java.util.Locale

class CoreTest {
    @Test fun `circle follows backend longitude latitude format regardless of phone locale`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val ring = Geo.circle(Geo.city, 400.0)
            assertEquals(24, ring.size)
            ring.forEach { assertTrue(kotlin.math.abs(Geo.distance(Geo.city, it) - 400) < 5) }
            val filter = RequestFilter(categories = listOf("Pothole", "Streetlight"), query = "  a+b ", polygon = listOf(Coordinate(38.8, -77.05), Coordinate(38.8, -77.04), Coordinate(38.81, -77.04)))
            val params = filter.parameters()
            assertEquals(listOf("Pothole", "Streetlight"), params.filter { it.first == "category" }.map { it.second })
            assertEquals("-77.050000,38.800000;-77.040000,38.800000;-77.040000,38.810000", params.first { it.first == "polygon" }.second)
            assertTrue(Api("https://example.test").url("api/requests", params).toString().contains("q=a%2Bb"))
        } finally { Locale.setDefault(old) }
    }
    @Test fun `bounds agree with iOS reporting form`() { assertTrue(Geo.inCity(Geo.city)); assertFalse(Geo.inCity(Coordinate(38.9, -77.03))) }
    @Test fun `links open cases and report paths without claiming City's scheme`() {
        assertEquals(Links.Destination.Case("26-00039674"), Links.destination("https://alex311visibility.me/r/26-00039674"))
        assertEquals(Links.Destination.Case("26-00039674"), Links.destination("${Links.scheme}://r/26-00039674"))
        assertEquals(Links.Destination.Report, Links.destination("${Links.scheme}://report"))
        assertEquals(Links.Destination.Mine, Links.destination("https://alex311visibility.me/submit/my"))
        assertNull(Links.destination("alex311://r/26-00039674")); assertNull(Links.destination("https://evil.test/r/26-00039674"))
        assertNull(Links.destination("https://alex311visibility.me/r/123")); assertNull(Links.destination("${Links.scheme}://r/../../etc"))
    }
    @Test fun `email links unwrap safe links and pasted messages`() {
        val link = "https://alex311visibility.me/submit/login?mode=signIn&oobCode=CODE123&tenantId=t"
        val safe = "https://nam12.safelinks.protection.outlook.com/?url=${URLEncoder.encode(link, "UTF-8")}&data=abc"
        assertEquals("CODE123", Links.oobCode(link)); assertEquals("CODE123", Links.oobCode(safe))
        assertEquals("CODE123", Links.pastedCode("Open this link:\n$safe\nWorks once."))
        assertNull(Links.oobCode("https://example.test/?mode=resetPassword&oobCode=X"))
        assertNull(Links.pastedCode("hello"))
        var wrapped = link
        repeat(5) { wrapped = "https://example.test/?link=${URLEncoder.encode(wrapped, "UTF-8")}" }
        assertNull(Links.oobCode(wrapped))
    }
    @Test fun `status changes exclude new cases and detect null transitions`() {
        val before = listOf(Request("A", status = "open"), Request("B"))
        val after = listOf(Request("A", status = "closed"), Request("B", status = "open"), Request("C", status = "open"))
        assertEquals(listOf("A", "B"), StatusChanges.changed(StatusChanges.snapshot(before), after).map { it.service_request_id })
    }
    @Test fun `older filing responses and optional null fields decode`() {
        val old = WireJson.decodeFromString<Attempt>("""{"attempt_id":7,"submit_state":"filing"}""")
        assertNull(old.photo_note); assertFalse(old.isFinal)
        val filed = WireJson.decodeFromString<Attempt>("""{"attempt_id":7,"submit_state":"filed","photos":2,"photo_note":"2 photos attached"}""")
        assertTrue(filed.isFinal); assertEquals(2, filed.photos)
        val request = WireJson.decodeFromString<Request>("""{"service_request_id":"26-00000001","media_ids":null,"related":null}""")
        assertTrue(request.media_ids.isEmpty()); assertTrue(request.related.isEmpty())
    }
}
