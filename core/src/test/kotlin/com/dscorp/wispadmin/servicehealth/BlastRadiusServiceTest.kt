package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.Evidence
import com.dscorp.wispadmin.servicehealth.port.HealthMaintenanceWindow
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagIncident
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagPort
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagProbe
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagTarget
import com.dscorp.wispadmin.servicehealth.port.HealthOltLogEvent
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

class BlastRadiusServiceTest {
    private val now=Instant.now()
    private val reader=mockk<HealthEvidenceReader>()
    private val events=mockk<HealthEventRepository>(relaxed=true)
    private val port=FakeNetDiagPort()
    private val affected=mockk<IncidentSubscriptionRepository>(relaxed=true)
    private val cursors=mockk<HealthCursorRepository>()
    private val target=HealthNetDiagTarget(id=10,name="PON-test",deviceRefId=1,parentTargetId=20,pollIntervalMs=60000,monitorConfig=null)
    private val rows=mutableListOf<IncidentSubscription>()
    private val service=BlastRadiusService(ServiceHealthProperties().apply {
        enabled=true; correlationEnabled=true; sharedIncidentsEnabled=true; pilotSubscriptionIds=setOf(1,2,3)
    },reader,events,availableProvider(port),affected,cursors,
        TransactionTemplate(mockk<PlatformTransactionManager>(relaxed=true)),ObjectMapper())
    init {
        every { events.save(any()) } answers { firstArg<HealthEvent>() }
        every { cursors.lock("blast-radius") } returns HealthCursor(cursorKey="blast-radius")
        every { affected.findByState(any()) } answers { val state=firstArg<String>(); rows.filter { it.state==state } }
        every { affected.findByIncidentIdAndSubscriptionId(any(),any()) } answers { rows.firstOrNull { it.incidentId==firstArg<Long>() && it.subscriptionId==secondArg<Int>() } }
        every { affected.save(any()) } answers { firstArg<IncidentSubscription>().also { if(it !in rows) rows+=it } }
        every { affected.findByIncidentId(any(),any()) } answers { PageImpl(rows.filter { it.incidentId==firstArg<Long>() }) }
    }
    private fun input(id: Int,state: String="offline",quality: Quality=Quality.FRESH)=HealthInputs(id,now,
        mapOf("ONU" to "sn$id","PON" to "1:0:1"),listOf(Evidence("OLT","run_state",now,state,quality)),emptyList(),
        listOf(OnuStateEvent(previousState="online",state="offline",observedAt=now.minusSeconds(60))),
        emptyList(),emptyList(),emptySet(),listOf(target),"ACTIVE",emptyMap())
    private fun prepare(ids: List<Int>,quality: Quality=Quality.FRESH) {
        every { events.findByEventStatus("OPEN") } returns ids.map { HealthEvent(id=it.toLong(),subscriptionId=it,diagnosisCode="GPON_DOWN",confidence=Confidence.MEDIUM) }
        ids.forEach { every { reader.read(it,any()) } returns input(it,quality=quality) }
    }
    @Test fun `three corroborated ONUs create one shared parent with all affected`() {
        prepare(listOf(1,2,3)); service.reconcile()
        assertEquals(1,port.incidents.size); assertEquals("GPON_SHARED_DOWN",port.incidents.single().reasonCode)
        assertEquals(setOf(1,2,3),rows.map { it.subscriptionId }.toSet())
        assertEquals(1,port.events.size)
    }
    @Test fun `existing acknowledged parent is reused and maintenance is recorded`() {
        port.incidents+=HealthNetDiagIncident(id=98,targetId=10,dedupKey="x",status="ACKNOWLEDGED",reasonCode="LINK_DOWN")
        port.maintenance+=HealthMaintenanceWindow(id=7,targetId=10)
        prepare(listOf(1)); service.reconcile()
        assertEquals(98,rows.single().incidentId); assertTrue(rows.single().scopeJson.contains("maintenance_ids"))
        assertEquals(1,port.incidents.size)
    }
    @Test fun `common collector failure never creates a shared access outage`() {
        prepare(listOf(1,2,3),Quality.STALE); service.reconcile()
        assertTrue(rows.isEmpty()); assertTrue(port.incidents.isEmpty())
    }
    @Test fun `partial recovery requires fresh online and leaves unknown subscriber affected`() {
        port.incidents+=HealthNetDiagIncident(id=98,targetId=10,dedupKey="x",status="SILENCED",reasonCode="LINK_DOWN")
        rows+=IncidentSubscription(incidentId=98,subscriptionId=1,state="AFFECTED")
        rows+=IncidentSubscription(incidentId=98,subscriptionId=2,state="AFFECTED")
        every { events.findByEventStatus("OPEN") } returns emptyList()
        every { reader.read(1,any()) } returns input(1,state="online")
        every { reader.read(2,any()) } returns input(2,state="online",quality=Quality.STALE)
        service.reconcile()
        assertEquals("RECOVERED",rows[0].state); assertEquals("AFFECTED",rows[1].state)
        assertEquals("SILENCED",port.incidents.single().status)
    }
    @Test fun `without netdiag port blast radius is skipped`() {
        val isolated=BlastRadiusService(ServiceHealthProperties().apply {
            enabled=true; correlationEnabled=true; sharedIncidentsEnabled=true; pilotSubscriptionIds=setOf(1,2,3)
        },reader,events,emptyProvider(),affected,cursors,
            TransactionTemplate(mockk<PlatformTransactionManager>(relaxed=true)),ObjectMapper())
        prepare(listOf(1,2,3)); isolated.reconcile()
        assertTrue(rows.isEmpty()); assertTrue(port.incidents.isEmpty())
    }

    private fun <T : Any> availableProvider(value: T): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns value
        return provider
    }

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }

    private class FakeNetDiagPort : HealthNetDiagPort {
        val incidents = mutableListOf<HealthNetDiagIncident>()
        val events = mutableListOf<String>()
        val maintenance = mutableListOf<HealthMaintenanceWindow>()
        private var nextId = 99L
        override fun enabledTargets() = emptyList<HealthNetDiagTarget>()
        override fun findTarget(id: Long) = null
        override fun latestProbe(targetId: Long) = null as HealthNetDiagProbe?
        override fun incidentsByTargetAndStatus(targetId: Long, status: String) =
            incidents.filter { it.targetId == targetId && it.status == status }
        override fun findIncident(id: Long) = incidents.firstOrNull { it.id == id }
        override fun findClosedByDedup(dedupKey: String, status: String) =
            incidents.firstOrNull { it.dedupKey == dedupKey && it.status == status }
        override fun saveIncident(
            target: HealthNetDiagTarget,
            dedupKey: String,
            status: String,
            severity: String,
            title: String,
            reasonCode: String,
            openedAt: Instant
        ): HealthNetDiagIncident {
            val saved = HealthNetDiagIncident(id = nextId++, targetId = target.id, dedupKey = dedupKey, status = status, reasonCode = reasonCode)
            incidents += saved
            return saved
        }
        override fun updateIncident(incident: HealthNetDiagIncident): HealthNetDiagIncident {
            incidents.removeIf { it.id == incident.id }
            incidents += incident
            return incident
        }
        override fun saveIncidentEvent(incident: HealthNetDiagIncident, type: String, payload: String, createdAt: Instant) {
            events += type
        }
        override fun findActiveMaintenance(now: Instant) = maintenance
        override fun findOltLogChanges(after: Instant, afterId: Long?, page: Pageable) = emptyList<HealthOltLogEvent>()
        override fun cpuThreshold() = 85
    }
}
