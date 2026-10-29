package com.herbertindustries.alex311

import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.domain.*
import com.herbertindustries.alex311.ui.friendly
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ResilienceTest {
    @Test fun `network failures read as plain language, deliberate messages pass through`() {
        assertEquals("You're offline. Check your connection and try again.", friendly(java.net.UnknownHostException("alex311-reborn.com")))
        assertEquals("Couldn't reach the server. Check your connection and try again.", friendly(java.net.ConnectException("failed to connect to /10.0.0.1 (port 443)")))
        assertEquals("The server took too long to answer. Try again.", friendly(java.net.SocketTimeoutException("timeout")))
        assertEquals("Please sign in again.", friendly(ApiException(401, "Please sign in again.")))
        assertEquals("Google sign-in was cancelled.", friendly(java.io.IOException("Google sign-in was cancelled.")))
    }
    @Test fun `mercator matches slippy-map tile math`() {
        val (x, y) = Geo.mercator(Geo.city)
        // Old Town Alexandria lies in OSM tile 16/18742/25092 (asinh(tan(lat)) formula).
        assertEquals(18742, (x * (1 shl 16)).toInt()); assertEquals(25092, (y * (1 shl 16)).toInt())
        assertEquals(0.5, Geo.mercator(Coordinate(0.0, 0.0)).first, 1e-12); assertEquals(0.5, Geo.mercator(Coordinate(0.0, 0.0)).second, 1e-12)
        assertTrue(Geo.mercator(Coordinate(90.0, 0.0)).second >= 0.0)
    }
    @Test fun `malformed map points report unreadable data instead of crashing`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"points":[]}"""))
            server.start()
            val error = runCatching { Api(server.url("/").toString()).points(RequestFilter()) }.exceptionOrNull()
            assertEquals("The server sent data this version of the app cannot read.", friendly(error!!))
        }
    }
}
