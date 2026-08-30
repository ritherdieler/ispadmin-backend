package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.controller.ServiceHealthController
import com.dscorp.wispadmin.servicehealth.service.*
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.service.genieacs.*
import com.dscorp.wispadmin.oltgateway.domain.repository.*
import com.dscorp.wispadmin.netdiag.domain.repository.*
import com.dscorp.wispadmin.traffic.repository.*
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.observability.security.ObservabilitySessionTokenService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.verifyNoInteractions
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
@MockBean(classes=[SubscriptionRepository::class,SubscriptionAcsRepository::class,OltMgrOnuRepository::class,OltMgrOltRepository::class,
    SubscriptionTrafficSampleRepository::class,TrafficSourceRunRepository::class,TrafficAnomalyEventRepository::class,
    NetDiagTargetRepository::class,NetDiagProbeRunRepository::class,NetDiagIncidentRepository::class,
    NetDiagIncidentEventRepository::class,NetDiagMaintenanceWindowRepository::class,NetDiagOltLogEventRepository::class,
    GenieAcsClient::class,ObservabilitySessionTokenService::class])
class HealthWiringTest {
    @Configuration
    @Import(HealthPersistenceTest.Config::class)
    @ComponentScan(basePackages=["com.dscorp.wispadmin.servicehealth"],excludeFilters=[ComponentScan.Filter(type=FilterType.REGEX,pattern=[".*Test.*"])])
    class Config {
        @Bean fun genieProperties()=GenieAcsProperties()
        @Bean fun trafficProperties()=TrafficProperties()
    }
    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var client: GenieAcsClient
    @Autowired lateinit var cursors: HealthCursorRepository
    @Test fun `complete service health graph starts with collectors disabled and NetDiag absent`() {
        assertNotNull(context.getBean(ServiceHealthController::class.java))
        assertNotNull(context.getBean(AcsTelemetryService::class.java))
        assertNotNull(context.getBean(IdentityChangeObserver::class.java))
        assertNotNull(context.getBean(RemoteActionService::class.java))
        assertTrue(cursors.existsById("actions"))
        verifyNoInteractions(client)
        val main=com.dscorp.wispadmin.wispadmin.WispAdminApplication::class.java
        assertTrue(main.getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication::class.java).scanBasePackages.contains("com.dscorp.wispadmin.servicehealth"))
        assertTrue(main.getAnnotation(org.springframework.boot.autoconfigure.domain.EntityScan::class.java).basePackages.contains("com.dscorp.wispadmin.servicehealth"))
        assertTrue(main.getAnnotation(org.springframework.data.jpa.repository.config.EnableJpaRepositories::class.java).basePackages.contains("com.dscorp.wispadmin.servicehealth"))
    }
}
