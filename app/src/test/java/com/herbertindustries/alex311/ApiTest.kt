package com.herbertindustries.alex311

import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.RequestFilter
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: Api
    @Before fun setup() { server = MockWebServer(); server.start(); api = Api(server.url("/").toString()) }
    @After fun cleanup() { server.shutdown() }
    @Test fun `session cookie comes only from expected cookie and goes only to submit endpoints`() = runTest {
        server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "other=ignore; Path=/").addHeader("Set-Cookie", "alex311_portal=secret-session; Path=/; HttpOnly; SameSite=Lax"))
        val token = api.createSession("firebase-id-token")
        assertEquals("secret-session", token)
        val create = server.takeRequest(); assertTrue(create.body.readUtf8().contains("\"id_token\":\"firebase-id-token\""))
        api.sessionToken = token
        server.enqueue(MockResponse().setBody("{\"links\":[]}")); api.get<MyLinks>("submit/api/my")
        assertEquals("alex311_portal=secret-session", server.takeRequest().getHeader("Cookie"))
        server.enqueue(MockResponse().setBody("{\"total\":0,\"rows\":[]}")); api.requests(RequestFilter())
        assertNull(server.takeRequest().getHeader("Cookie"))
    }
    @Test fun `unauthorized expires local session and rate limits expose retry-after`() = runTest {
        var expired = false; api.sessionToken = "old"; api.onUnauthorized = { expired = true }
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        try { api.get<MyLinks>("submit/api/my"); fail() } catch (e: ApiException) { assertEquals(401, e.status) }
        assertTrue(expired)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody("{\"detail\":\"Try later\"}"))
        try { api.fire("POST", "submit/api/email-link"); fail() } catch (e: ApiException) { assertEquals(60L, e.retryAfter); assertEquals("Try later", e.message) }
    }
    @Test fun `an old response cannot expire a newly signed-in session`() = runTest {
        var expired = false
        api.sessionToken = "old-account"; api.onUnauthorized = { expired = true }
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val result = async { runCatching { api.get<MyLinks>("submit/api/my") } }
        withContext(Dispatchers.IO) { server.takeRequest() }
        api.sessionToken = "new-account"
        assertTrue(result.await().isFailure)
        assertFalse(expired)
        assertEquals("new-account", api.sessionToken)
    }
    @Test fun `registry uses quoted etags and handles not-modified`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))
        assertNull(api.registry("\"version1\""))
        assertEquals("\"version1\"", server.takeRequest().getHeader("If-None-Match"))
    }
    @Test fun `points decode category indices and unknown statuses`() = runTest {
        server.enqueue(MockResponse().setBody("""{"total":2,"categories":["Pothole"],"points":[["A",38.8,-77.1,"open",0,2],["B",38.81,-77.11,null,99,-1]]}"""))
        val points = api.points(RequestFilter())
        assertEquals("Pothole", points[0].category); assertEquals("Unknown", points[1].category); assertNull(points[1].status)
    }
    @Test fun `photos upload raw JPEG with correct name and cookie`() = runTest {
        api.sessionToken = "token"; server.enqueue(MockResponse().setBody("{}"))
        val bytes = byteArrayOf(-1, -40, -1)
        api.raw("POST", "submit/api/attempt/7/photos", query = listOf("name" to "photo-1.jpg"), bytes = bytes)
        val request = server.takeRequest()
        assertEquals("image/jpeg", request.getHeader("Content-Type")); assertArrayEquals(bytes, request.body.readByteArray()); assertEquals("/submit/api/attempt/7/photos?name=photo-1.jpg", request.path)
    }
    @Test fun `redirects cannot forward a session cookie to a different origin`() = runTest {
        api.sessionToken = "secret"
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.test/stolen"))
        try { api.raw("GET", "submit/api/my"); fail() } catch (e: ApiException) { assertEquals(302, e.status) }
        assertEquals(1, server.requestCount)
    }
}
