package com.herbertindustries.alex311.data

import java.security.SecureRandom
import java.util.Base64

internal object SignInNonce {
    private val random = SecureRandom()

    fun create(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
