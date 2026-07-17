package com.dscorp.wispadmin.oltgateway.domain

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltModelRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import javax.persistence.EntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:olt_mgr_assoc;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
    ]
)
class OltMgrAssociationJpaTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = ["com.dscorp.wispadmin.oltgateway.domain.entity"])
    @EnableJpaRepositories(basePackages = ["com.dscorp.wispadmin.oltgateway.domain.repository"])
    class TestApp

    @Autowired
    private lateinit var oltRepository: OltMgrOltRepository

    @Autowired
    private lateinit var oltModelRepository: OltMgrOltModelRepository

    @Autowired
    private lateinit var zoneRepository: OltMgrZoneRepository

    @Autowired
    private lateinit var onuRepository: OltMgrOnuRepository

    @Autowired
    private lateinit var statusRepository: OltMgrOnuStatusCurrentRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `persist onu with olt zone and mapsId status`() {
        val model = oltModelRepository.save(
            OltMgrOltModel(
                code = "MA5608T",
                vendor = "Huawei",
                product = "MA5608T",
                family = "MA5600T",
                maxConcurrentCliSessions = 4
            )
        )
        val olt = oltRepository.save(
            OltMgrOlt(
                name = "test-olt",
                ipAddress = "10.11.104.2",
                usernameEnc = "u",
                passwordEnc = "p",
                model = model
            )
        )
        val zone = zoneRepository.save(OltMgrZone(name = "ZonaTest"))
        val onu = OltMgrOnu(
            sn = "SNJPA0001",
            externalId = "test-olt_0_1_1",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 1,
            zone = zone,
            name = "jpa-onu",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedOnu = onuRepository.saveAndFlush(onu)
        val status = OltMgrOnuStatusCurrent(
            onu = savedOnu,
            runState = "online",
            matchState = "match",
            polledAt = Instant.now()
        )
        savedOnu.status = status
        statusRepository.saveAndFlush(status)
        entityManager.clear()

        val reloaded = onuRepository.findById(savedOnu.id!!).orElseThrow()
        assertEquals(olt.id, reloaded.olt.id)
        assertEquals(zone.id, reloaded.zone?.id)
        assertEquals(model.id, reloaded.olt.model?.id)
        assertEquals(4, reloaded.olt.model?.maxConcurrentCliSessions)
        val reloadedStatus = statusRepository.findById(savedOnu.id!!).orElseThrow()
        assertEquals(savedOnu.id, reloadedStatus.onuId)
        assertEquals("online", reloadedStatus.runState)
        assertEquals(savedOnu.id, reloadedStatus.onu.id)
    }

    @Test
    fun `onu without zone persists with null association`() {
        val olt = oltRepository.save(
            OltMgrOlt(
                name = "test-olt-2",
                ipAddress = "10.11.104.3",
                usernameEnc = "u",
                passwordEnc = "p"
            )
        )
        val saved = onuRepository.saveAndFlush(
            OltMgrOnu(
                sn = "SNJPA0002",
                externalId = "test-olt-2_0_1_2",
                olt = olt,
                board = 0,
                port = 1,
                onuIndex = 2
            )
        )
        entityManager.clear()
        val reloaded = onuRepository.findById(saved.id!!).orElseThrow()
        assertNotNull(reloaded.olt.id)
        assertNull(reloaded.zone)
    }
}
