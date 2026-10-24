package com.herbertindustries.alex311

import com.herbertindustries.alex311.data.Api
import com.herbertindustries.alex311.domain.Links
import org.junit.Assert.*
import org.junit.Test

class LinksTest {
    private val origins = listOf("https://alex311-reborn.com", "https://alex311visibility.me")

    @Test fun `default API origin and shared case links use canonical host`() {
        assertEquals("https://alex311-reborn.com", Api.PRODUCTION)
        assertEquals("https://alex311-reborn.com/r/26-00039674", Api().url("r/26-00039674").toString())
    }

    @Test fun `both website hosts route supported paths`() {
        origins.forEach { origin ->
            assertEquals(Links.Destination.Case("26-00039674"), Links.destination("$origin/r/26-00039674"))
            assertEquals(Links.Destination.Report, Links.destination("$origin/submit"))
            assertEquals(Links.Destination.Mine, Links.destination("$origin/submit/my?source=digest"))
            assertEquals(Links.Destination.SignIn(null), Links.destination("$origin/submit/login"))
        }
    }

    @Test fun `website and custom sign in preserve encoded handoff query`() {
        val query = "mode=signIn&oobCode=A%2BB%2FC%3D&apiKey=public-id&continueUrl=https%3A%2F%2Falex311-reborn.com%2Fsubmit%2Fmy&lang=en&tenantId=alex311-qfnem"
        (origins.map { "$it/submit/login" } + "${Links.scheme}://signin").forEach { url ->
            assertEquals(Links.Destination.SignIn("A+B/C="), Links.destination("$url?$query"))
        }
    }

    @Test fun `home screen shortcuts route through the app scheme only`() {
        assertEquals(Links.Destination.Report, Links.destination("${Links.scheme}://report"))
        assertEquals(Links.Destination.Nearby, Links.destination("${Links.scheme}://nearby"))
        origins.forEach { assertNull(Links.destination("$it/nearby")) }
    }

    @Test fun `browser only paths cannot become app routes even with sign in query`() {
        val paths = listOf("/", "/api/requests", "/submit/api/my", "/submit/digest/confirm", "/submit/digest/unsubscribe", "/submit/account", "/submit/admin", "/submit/login/extra", "/submit/my/extra", "/submit/", "/my", "/report")
        origins.forEach { origin ->
            paths.forEach { path ->
                assertNull("$origin$path", Links.destination("$origin$path?mode=signIn&oobCode=CODE"))
            }
        }
        listOf("https://evil.test", "https://alex311-reborn.com.evil.test", "http://alex311-reborn.com").forEach { origin ->
            assertNull(Links.destination("$origin/submit/login?mode=signIn&oobCode=CODE"))
        }
    }
}
