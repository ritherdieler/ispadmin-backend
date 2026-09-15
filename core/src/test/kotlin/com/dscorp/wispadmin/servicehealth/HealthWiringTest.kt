package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.controller.ServiceHealthController
import com.dscorp.wispadmin.servicehealth.service.*
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.CpeProvisionFlagPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionActionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.shared.security.ObservabilitySessionTokenService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.*

@SpringBootTest(classes=[HealthWiringTest.Config::class],webEnvironment=SpringBootTest.WebEnvironment.NONE,properties=[
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.datasource.url=jdbc:h2:mem:wiring;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "service.health.enabled=false","net.diag.enabled=false"])
@MockBean(classes=[ObservabilitySessionTokenService::class, SubscriptionDirectoryPort::class, AcsSubscriptionPort::class, SubscriptionActionPort::class, CpeProvisionFlagPort::class])
class HealthWiringTest {
    @Import(HealthPersistenceTest.Config::class)
    @ComponentScan(basePackages=["com.dscorp.wispadmin.servicehealth"],excludeFilters=[ComponentScan.Filter(type=FilterType.REGEX,pattern=[".*Test.*"])])
    class Config {
        @Bean fun environmentProperties()=GigafiberEnvironmentProperties()
    }
    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var cursors: HealthCursorRepository
    @Test fun `complete service health graph starts with collectors disabled and NetDiag absent`() {
        assertNotNull(context.getBean(ServiceHealthController::class.java))
        assertNotNull(context.getBean(CpeInformPersistService::class.java))
        assertNotNull(context.getBean(HealthSnapshotIngestService::class.java))
        assertNotNull(context.getBean(IdentityChangeObserver::class.java))
        assertNotNull(context.getBean(RemoteActionService::class.java))
        assertTrue(cursors.existsById("actions"))
    }
}
