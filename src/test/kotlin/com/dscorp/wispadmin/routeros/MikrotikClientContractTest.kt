package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

abstract class MikrotikClientContractTest {

    protected abstract fun createClient(): MikrotikClient

    protected abstract fun validDevice(): MikrotikDeviceRef

    protected abstract fun authFailDevice(): MikrotikDeviceRef

    @Test
    fun `print system identity returns non-blank name`() {
        val client = createClient()
        val rows = client.withSession(validDevice()) { session ->
            session.print("/system/identity")
        }
        assertFalse(rows.isEmpty())
        val name = rows.first()["name"].orEmpty()
        assertTrue(name.isNotBlank(), "identity.name must not be blank")
    }

    @Test
    fun `print system resource returns version`() {
        val client = createClient()
        val rows = client.withSession(validDevice()) { session ->
            session.print("/system/resource")
        }
        assertFalse(rows.isEmpty())
        val version = rows.first()["version"].orEmpty()
        assertTrue(version.isNotBlank(), "resource.version must not be blank")
    }

    @Test
    fun `print with impossible query returns empty list`() {
        val client = createClient()
        val rows = client.withSession(validDevice()) { session ->
            session.print(
                "/system/identity",
                mapOf("name" to "__netdiag_impossible_identity_name__")
            )
        }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `invalid credentials throw MikrotikAuthException`() {
        val client = createClient()
        assertThrows(MikrotikAuthException::class.java) {
            client.withSession(authFailDevice()) { session ->
                session.print("/system/identity")
            }
        }
    }
}
