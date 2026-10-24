package com.herbertindustries.alex311

import com.herbertindustries.alex311.data.SignInNonce
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SignInNonceTest {
    @Test fun `sign in attempts have fresh 256 bit URL safe nonces`() {
        val nonces = List(100) { SignInNonce.create() }
        assertEquals(nonces.size, nonces.toSet().size)
        nonces.forEach { nonce ->
            assertTrue(nonce.matches(Regex("[A-Za-z0-9_-]{43}")))
            assertEquals(32, Base64.getUrlDecoder().decode(nonce).size)
        }
    }
}
