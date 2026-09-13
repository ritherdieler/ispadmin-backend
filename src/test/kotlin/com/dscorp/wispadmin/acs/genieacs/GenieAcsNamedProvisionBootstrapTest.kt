package com.dscorp.wispadmin.acs.genieacs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments

class GenieAcsNamedProvisionBootstrapTest {

    @Test
    fun `classpath contains the three named provisions`() {
        val bootstrap = GenieAcsNamedProvisionBootstrap(mockk(relaxed = true))
        NamedGenieAcsProvisions.ids.forEach { id ->
            val script = bootstrap.loadScript(id)
            assertNotNull(script, id)
            assertTrue(script!!.isNotBlank(), id)
        }
    }

    @Test
    fun `boot PUT does not throw when NBI is down`() {
        val client = mockk<GenieAcsClient>()
        every { client.putProvision(any(), any()) } throws IllegalStateException("NBI down")
        val bootstrap = GenieAcsNamedProvisionBootstrap(client)

        bootstrap.run(DefaultApplicationArguments())

        verify(exactly = 3) { client.putProvision(any(), any()) }
    }
}
