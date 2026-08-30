package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.Evidence
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.service.*
import com.dscorp.wispadmin.netdiag.domain.entity.*
import com.dscorp.wispadmin.netdiag.domain.repository.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional

class BlastRadiusServiceTest {
    private val now=Instant.now()
    private val reader=mockk<HealthEvidenceReader>()
    private val events=mockk<HealthEventRepository>(relaxed=true)
    private val incidents=mockk<NetDiagIncidentRepository>(relaxed=true)
    private val incidentEvents=mockk<NetDiagIncidentEventRepository>(relaxed=true)
    private val affected=mockk<IncidentSubscriptionRepository>(relaxed=true)
    private val maintenance=mockk<NetDiagMaintenanceWindowRepository>(relaxed=true)
    private val cursors=mockk<HealthCursorRepository>()
    private val target=NetDiagTarget(id=10,name="PON-test",parentTargetId=20)
    private val rows=mutableListOf<IncidentSubscription>()
    private val parents=mutableListOf<NetDiagIncident>()
    private val service=BlastRadiusService(ServiceHealthProperties().apply {
        enabled=true; correlationEnabled=true; sharedIncidentsEnabled=true; pilotSubscriptionIds=setOf(1,2,3)
    },reader,events,incidents,incidentEvents,affected,maintenance,cursors,
        TransactionTemplate(mockk<PlatformTransactionManager>(relaxed=true)),ObjectMapper())
    init {
        every { events.save(any()) } answers { firstArg<HealthEvent>() }
        every { incidentEvents.save(any()) } answers { firstArg<NetDiagIncidentEvent>() }
        every { cursors.lock("blast-radius") } returns HealthCursor(cursorKey="blast-radius")
        every { affected.findByState(any()) } answers { val state=firstArg<String>(); rows.filter { it.state==state } }
        every { affected.findByIncidentIdAndSubscriptionId(any(),any()) } answers { rows.firstOrNull { it.incidentId==firstArg<Long>() && it.subscriptionId==secondArg<Int>() } }
        every { affected.save(any()) } answers { firstArg<IncidentSubscription>().also { if(it !in rows) rows+=it } }
        every { affected.findByIncidentId(any(),any()) } answers { PageImpl(rows.filter { it.incidentId==firstArg<Long>() }) }
        every { incidents.findByTarget_IdAndStatus(any(),any()) } answers { parents.filter { it.target?.id==firstArg<Long>() && it.status==secondArg<String>() } }
        every { incidents.save(any()) } answers { firstArg<NetDiagIncident>().also { if(it.id==null) it.id=99; if(it !in parents) parents+=it } }
        every { incidents.findById(any()) } answers { Optional.ofNullable(parents.firstOrNull { it.id==firstArg<Long>() }) }
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
        assertEquals(1,parents.size); assertEquals("GPON_SHARED_DOWN",parents.single().reasonCode)
        assertEquals(setOf(1,2,3),rows.map { it.subscriptionId }.toSet())
        verify(exactly=1) { incidentEvents.save(any()) }
    }
    @Test fun `existing acknowledged parent is reused and maintenance is recorded`() {
        parents+=NetDiagIncident(id=98,target=target,status="ACKNOWLEDGED",reasonCode="LINK_DOWN")
        every { maintenance.findActiveAt(any()) } returns listOf(NetDiagMaintenanceWindow(id=7,targetId=10))
        prepare(listOf(1)); service.reconcile()
        assertEquals(98,rows.single().incidentId); assertTrue(rows.single().scopeJson.contains("maintenance_ids"))
        verify(exactly=0) { incidents.save(any()) }
    }
    @Test fun `common collector failure never creates a shared access outage`() {
        prepare(listOf(1,2,3),Quality.STALE); service.reconcile()
        assertTrue(rows.isEmpty()); assertTrue(parents.isEmpty())
    }
    @Test fun `partial recovery requires fresh online and leaves unknown subscriber affected`() {
        parents+=NetDiagIncident(id=98,target=target,status="SILENCED",reasonCode="LINK_DOWN")
        rows+=IncidentSubscription(incidentId=98,subscriptionId=1,state="AFFECTED")
        rows+=IncidentSubscription(incidentId=98,subscriptionId=2,state="AFFECTED")
        every { events.findByEventStatus("OPEN") } returns emptyList()
        every { reader.read(1,any()) } returns input(1,state="online")
        every { reader.read(2,any()) } returns input(2,state="online",quality=Quality.STALE)
        service.reconcile()
        assertEquals("RECOVERED",rows[0].state); assertEquals("AFFECTED",rows[1].state)
        assertEquals("SILENCED",parents.single().status)
    }
}
