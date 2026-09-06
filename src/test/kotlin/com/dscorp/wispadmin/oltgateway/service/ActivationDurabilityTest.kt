package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.RecordingEventBus
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.client.*
import com.dscorp.wispadmin.oltgateway.dto.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.core.io.ClassPathResource
import java.util.UUID
import java.util.concurrent.Executor

class ActivationDurabilityTest {
    @Test fun `restart preserves identity and resumes ACS without repeating authorization`() {
        val db=DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1","sa","")
        ResourceDatabasePopulator(ClassPathResource("db/oltgateway/V1__activation_operation.sql")).execute(db)
        val jdbc=JdbcTemplate(db)
        val json=jacksonObjectMapper().findAndRegisterModules()
        fun journal()=JdbcActivationJournal(jdbc,json,"test-key-at-least-thirty-two-characters")
        val facade=mockk<OltManagerFacade>()
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(status=true,unique_external_id="external-1")
        val acs=mockk<AcsCpeClient>()
        every { acs.provision(any()) } returns AcsCpeProvisionResponse("SN1",CpeProvisionStatus.COMPLETE)
        val events=RecordingEventBus()
        val request=OnuActivateRequestDto(sn="SN1",oltId="1",board="1",port="1",wifiPassword24="never-store-in-clear")
        val service=OnuActivationService(facade,acs,events,journal(),Executor { })
        service.activate(request)
        assertFalse(jdbc.queryForObject("select request_cipher from olt_activation_operation where sn='SN1'",String::class.java)!!.contains("never-store-in-clear"))
        jdbc.update("update olt_activation_operation set lease_until=0")
        val restarted=OnuActivationService(facade,acs,events,journal(),Executor { it.run() })
        restarted.recover()
        assertEquals("SN1",restarted.statusByExternalId("external-1")!!.sn)
        assertEquals(CpeProvisionStatus.COMPLETE,restarted.statusBySn("sn1")!!.cpeStatus)
        restarted.activate(request)
        verify(exactly=1) { facade.authorizeOnu(any()) }
        verify(exactly=1) { acs.provision(any()) }
    }
}
