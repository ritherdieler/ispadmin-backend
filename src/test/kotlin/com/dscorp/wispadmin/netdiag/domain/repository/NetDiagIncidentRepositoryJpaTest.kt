package com.dscorp.wispadmin.netdiag.domain.repository

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import org.hibernate.Hibernate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import javax.persistence.EntityManager

@DataJpaTest
@ContextConfiguration(classes = [NetDiagIncidentRepositoryJpaTest.TestApp::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:netdiag_incident_repo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
    ]
)
class NetDiagIncidentRepositoryJpaTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = ["com.dscorp.wispadmin.netdiag.domain.entity"])
    @EnableJpaRepositories(basePackages = ["com.dscorp.wispadmin.netdiag.domain.repository"])
    class TestApp

    @Autowired
    private lateinit var incidentRepository: NetDiagIncidentRepository

    @Autowired
    private lateinit var targetRepository: NetDiagTargetRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var target: NetDiagTarget

    @BeforeEach
    fun seed() {
        incidentRepository.deleteAll()
        targetRepository.deleteAll()
        target = targetRepository.save(NetDiagTarget(name = "MK1", deviceRefId = 7L))
        incidentRepository.save(
            incident(
                status = "OPEN",
                severity = "P0",
                reasonCode = "LINK_DOWN",
                dedupKey = "LINK_DOWN:1:ether1",
                openedAt = Instant.parse("2026-07-26T12:00:00Z")
            )
        )
        incidentRepository.save(
            incident(
                status = "ACKNOWLEDGED",
                severity = "P1",
                reasonCode = "POLL_STALE",
                dedupKey = "POLL_STALE:1:poll",
                openedAt = Instant.parse("2026-07-27T12:00:00Z")
            )
        )
        incidentRepository.save(
            incident(
                status = "RESOLVED",
                severity = "P0",
                reasonCode = "LINK_DOWN",
                dedupKey = "LINK_DOWN:1:ether2",
                openedAt = Instant.parse("2026-07-25T12:00:00Z")
            )
        )
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `findForList filtra por estados y carga target sin lazy`() {
        val result = incidentRepository.findForList(
            listOf("OPEN", "ACKNOWLEDGED", "SILENCED"),
            null,
            null,
            null,
            null
        )

        assertEquals(listOf("ACKNOWLEDGED", "OPEN"), result.map { it.status })
        assertTrue(result.all { Hibernate.isInitialized(it.target) })
        assertEquals("MK1", result.first().target?.name)
    }

    @Test
    fun `findForList aplica severity targetId y rango de fechas`() {
        val result = incidentRepository.findForList(
            listOf("OPEN", "RESOLVED"),
            "P0",
            target.id,
            Instant.parse("2026-07-26T00:00:00Z"),
            Instant.parse("2026-07-26T23:59:59Z")
        )

        assertEquals(1, result.size)
        assertEquals("OPEN", result.first().status)
    }

    @Test
    fun `findByIdWithTarget inicializa target`() {
        val id = incidentRepository.findForList(listOf("OPEN"), null, null, null, null).first().id!!
        entityManager.clear()

        val found = incidentRepository.findByIdWithTarget(id).orElseThrow()

        assertTrue(Hibernate.isInitialized(found.target))
        assertEquals("MK1", found.target?.name)
    }

    @Test
    fun `counts agregados por status severity y reasonCode`() {
        val statuses = listOf("OPEN", "ACKNOWLEDGED")

        assertEquals(2L, incidentRepository.countByStatusIn(statuses))
        assertEquals(1L, incidentRepository.countBySeverityAndStatusIn("P0", statuses))
        assertEquals(1L, incidentRepository.countByReasonCodeAndStatusIn("POLL_STALE", statuses))
    }

    @Test
    fun `findByTarget_IdAndStatusAndReasonCode filtra en base de datos`() {
        val result = incidentRepository.findByTarget_IdAndStatusAndReasonCode(
            target.id!!,
            "OPEN",
            "LINK_DOWN"
        )

        assertEquals(1, result.size)
        assertEquals("LINK_DOWN:1:ether1", result.first().dedupKey)
    }

    private fun incident(
        status: String,
        severity: String,
        reasonCode: String,
        dedupKey: String,
        openedAt: Instant
    ): NetDiagIncident {
        return NetDiagIncident(
            target = target,
            dedupKey = dedupKey,
            status = status,
            severity = severity,
            title = "Incident $dedupKey",
            reasonCode = reasonCode,
            openedAt = openedAt
        )
    }
}
