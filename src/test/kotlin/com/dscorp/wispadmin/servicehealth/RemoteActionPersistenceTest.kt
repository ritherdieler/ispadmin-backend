package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.servicehealth.controller.HealthActor
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.service.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.service.genieacs.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest
@ContextConfiguration(classes=[HealthPersistenceTest.Config::class])
@TestPropertySource(properties=["spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.datasource.url=jdbc:h2:mem:actions;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"])
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RemoteActionPersistenceTest {
    @Autowired lateinit var actions: RemoteActionRepository
    @Autowired lateinit var cursors: HealthCursorRepository
    @Autowired lateinit var wifi: WifiCurrentRepository
    @Autowired lateinit var manager: PlatformTransactionManager
    private lateinit var remote: RemoteActionService
    private lateinit var client: GenieAcsClient
    private lateinit var acs: SubscriptionAcsRepository
    private val actor=HealthActor(1,"TECHNICIAN")
    @BeforeEach fun setup() {
        actions.deleteAll(); cursors.deleteAll(); wifi.deleteAll()
        cursors.saveAndFlush(HealthCursor(cursorKey="actions"))
        val subscriptions=mockk<SubscriptionRepository>()
        val identity=mockk<IdentityService>()
        acs=mockk(); client=mockk()
        for(id in 1..6) {
            every { subscriptions.findById(id) } returns Optional.of(Subscription(id=id,fiberOnu=Onu(sn="sn$id"),equipmentCondition=EquipmentCondition.values().first()))
            every { identity.resolveOnu("sn$id") } returns id
            every { acs.findById(id) } returns Optional.of(SubscriptionAcs(subscriptionId=id,genieacsDeviceId="device$id"))
            every { identity.resolveAcs("device$id") } returns id
        }
        val properties=ServiceHealthProperties().apply { enabled=true; actionsEnabled=true; configEnabled=true; pilotSubscriptionIds=(1..6).toSet(); stationHmacKey="k".repeat(32) }
        val scope=ServiceHealthScope(properties,GigafiberEnvironmentProperties(),acs)
        remote=RemoteActionService(properties,scope,actions,cursors,subscriptions,acs,wifi,identity,client,GenieAcsProperties(),TransactionTemplate(manager),ObjectMapper())
    }
    @Test fun `parallel double click makes one durable reservation`() {
        val pool=Executors.newFixedThreadPool(4)
        try {
            val results=pool.invokeAll((1..8).map { Callable { remote.reserve(1,actor,"request-1","REBOOT_ONU","digest",false) } }).map { it.get(10,TimeUnit.SECONDS) }
            assertEquals(1,results.count { it.second })
            assertEquals(1,results.map { it.first.id }.distinct().size)
            assertEquals(1,actions.count())
        } finally { pool.shutdownNow() }
    }
    @Test fun `ACS concurrency allows at most three physical devices`() {
        val pool=Executors.newFixedThreadPool(6)
        try {
            val results=pool.invokeAll((1..6).map { id -> Callable {
                try { remote.reserve(id,actor,"request-$id","WIFI_REFRESH","digest",true); 202 }
                catch(ex: ResponseStatusException) { ex.rawStatusCode }
            } }).map { it.get(10,TimeUnit.SECONDS) }
            assertEquals(3,results.count { it==202 }); assertEquals(3,results.count { it==429 })
        } finally { pool.shutdownNow() }
    }
    @Test fun `different endpoint cannot bypass cooldown and payload key cannot be reused`() {
        remote.reserve(1,actor,"request-1","REBOOT_ONU","digest",false)
        assertEquals(429,assertThrows(ResponseStatusException::class.java) { remote.reserve(1,actor,"request-2","CONFIG","digest",true) }.rawStatusCode)
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { remote.reserve(1,actor,"request-1","REBOOT_ONU","other",false) }.rawStatusCode)
    }
    @Test fun `accepted task remains pending until newer sample and preserves task id`() {
        val action=remote.reserve(1,actor,"request-1","WIFI_REFRESH","digest",true).first
        remote.finish(action.id!!,"PENDING","task-1")
        remote.confirmPending()
        assertEquals("PENDING",actions.findById(action.id!!).get().status)
        wifi.saveAndFlush(WifiCurrent(subscriptionId=1,deviceId="device1",qualityStatus=Quality.FRESH,observedAt=action.createdAt.plusSeconds(1)))
        remote.confirmPending()
        val result=actions.findById(action.id!!).get()
        assertEquals("CONFIRMED",result.status); assertEquals("task-1",result.taskId)
        verify { client wasNot Called }
    }
    @Test fun `technician cannot submit WAN before any device IO`() {
        assertEquals(403,assertThrows(ResponseStatusException::class.java) {
            remote.configure(1,actor,"request-1",CpeConfiguration(network=NetworkConfiguration(ipAddress="10.0.0.1")))
        }.rawStatusCode)
        verify { client wasNot Called }; verify(exactly=0) { acs.findById(any()) }
        assertEquals(0,actions.count())
    }
}
