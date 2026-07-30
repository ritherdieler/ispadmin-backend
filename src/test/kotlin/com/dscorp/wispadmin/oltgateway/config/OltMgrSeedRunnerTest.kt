package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltModelRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Optional

class OltMgrSeedRunnerTest {

    private val oltRepository = mockk<OltMgrOltRepository>()
    private val modelRepository = mockk<OltMgrOltModelRepository>()
    private val properties = OltGatewayProperties().apply {
        enabled = true
        oltId = "gigafiber-ma5608t"
        modelCode = "MA5608T"
        host = "10.11.104.2"
        username = "oltadmin"
        password = "secret"
        session.poolSize = 4
    }

    @Test
    fun `seed crea modelo MA5608T con max 4 y asocia olt`() {
        every { modelRepository.findByCode("MA5608T") } returns Optional.empty()
        val modelSlot = slot<OltMgrOltModel>()
        every { modelRepository.save(capture(modelSlot)) } answers {
            firstArg<OltMgrOltModel>().also { it.id = 1L }
        }
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.empty()
        val oltSlot = slot<OltMgrOlt>()
        every { oltRepository.save(capture(oltSlot)) } answers { firstArg() }

        OltMgrSeedRunner(oltRepository, modelRepository, properties).run(null)

        assertEquals("MA5608T", modelSlot.captured.code)
        assertEquals(4, modelSlot.captured.maxConcurrentCliSessions)
        assertEquals(modelSlot.captured, oltSlot.captured.model)
    }

    @Test
    fun `seed backfill model cuando olt existe sin modelo`() {
        val model = OltMgrOltModel(id = 2L, code = "MA5608T", maxConcurrentCliSessions = 4)
        every { modelRepository.findByCode("MA5608T") } returns Optional.of(model)
        val olt = OltMgrOlt(id = 9L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2", model = null)
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { oltRepository.save(any()) } answers { firstArg() }

        OltMgrSeedRunner(oltRepository, modelRepository, properties).run(null)

        assertNotNull(olt.model)
        assertEquals(2L, olt.model?.id)
        verify(exactly = 1) { oltRepository.save(olt) }
    }

    @Test
    fun `seed backfill password cuando olt existe sin password`() {
        val model = OltMgrOltModel(id = 2L, code = "MA5608T", maxConcurrentCliSessions = 4)
        every { modelRepository.findByCode("MA5608T") } returns Optional.of(model)
        val olt = OltMgrOlt(
            id = 9L,
            name = "gigafiber-ma5608t",
            ipAddress = "10.11.104.2",
            model = model,
            usernameEnc = "oltadmin",
            passwordEnc = ""
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { oltRepository.save(any()) } answers { firstArg() }

        OltMgrSeedRunner(oltRepository, modelRepository, properties).run(null)

        assertEquals("secret", olt.passwordEnc)
        verify(exactly = 1) { oltRepository.save(olt) }
    }
}
