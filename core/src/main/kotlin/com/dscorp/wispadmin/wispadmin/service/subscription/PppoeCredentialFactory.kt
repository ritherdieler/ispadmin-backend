package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeProfileCatalog
import java.security.SecureRandom

object PppoeCredentialFactory {

    const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val PASSWORD_LENGTH = 20

    private val random = SecureRandom()

    fun username(subscriptionId: Int?): String? = PppoeProfileCatalog.username(subscriptionId)

    fun password(): String = buildString(PASSWORD_LENGTH) {
        repeat(PASSWORD_LENGTH) {
            append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
    }
}
