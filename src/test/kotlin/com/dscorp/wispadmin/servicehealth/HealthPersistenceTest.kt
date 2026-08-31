package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import javax.persistence.EntityManager
import java.time.Instant

@DataJpaTest
@ContextConfiguration(classes=[HealthPersistenceTest.Config::class])
@TestPropertySource(properties=["spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.jdbc.time_zone=America/Lima","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.datasource.url=jdbc:h2:mem:servicehealth;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"])
class HealthPersistenceTest {
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackageClasses=[OpticalSample::class])
    @EnableJpaRepositories(basePackageClasses=[OpticalSampleRepository::class])
    class Config
    @Autowired lateinit var optical: OpticalSampleRepository
    @Autowired lateinit var counts: WifiCountSampleRepository
    @Autowired lateinit var links: IdentityLinkRepository
    @Autowired lateinit var cursors: HealthCursorRepository
    @Autowired lateinit var em: EntityManager
    @Test fun `constant optics remain distinct fresh observations and null is not carried forward`() {
        val at=Instant.parse("2026-08-30T15:00:00Z")
        optical.saveAndFlush(OpticalSample(subscriptionId=1,onuId=10,onuRxDbm=-20.0,observedAt=at))
        optical.saveAndFlush(OpticalSample(subscriptionId=1,onuId=10,onuRxDbm=-20.0,observedAt=at.plusSeconds(300)))
        optical.saveAndFlush(OpticalSample(subscriptionId=1,onuId=10,onuRxDbm=null,observedAt=at.plusSeconds(600),qualityStatus=Quality.MISSING))
        em.clear()
        assertEquals(3,optical.findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(1,at,at.plusSeconds(600)).size)
        assertNull(optical.findTopBySubscriptionIdOrderByObservedAtDesc(1)!!.onuRxDbm)
    }
    @Test fun `partial session can be completed without adding a second reading`() {
        val at=Instant.parse("2026-08-30T15:00:00Z")
        counts.saveAndFlush(WifiCountSample(subscriptionId=1,deviceId="device",informAt=at,observedAt=at))
        em.clear()
        val same=counts.findByDeviceIdAndSubscriptionIdAndInformAt("device",1,at)!!
        same.associatedDeviceCount=0; same.qualityStatus=Quality.FRESH
        counts.saveAndFlush(same); em.clear()
        assertEquals(1,counts.count())
        assertEquals(0,counts.findByDeviceIdAndSubscriptionIdAndInformAt("device",1,at)!!.associatedDeviceCount)
    }
    @Test fun `wifi samples are unique on parameter timestamp not Inform`() {
        val wifiAt=Instant.parse("2026-08-30T15:00:00Z")
        counts.saveAndFlush(WifiCountSample(subscriptionId=1,deviceId="device",informAt=wifiAt,observedAt=wifiAt,
            associatedDeviceCount=3,associated2g=3,associated5g=0,qualityStatus=Quality.FRESH))
        org.junit.jupiter.api.assertThrows<org.springframework.dao.DataIntegrityViolationException> {
            counts.saveAndFlush(WifiCountSample(subscriptionId=1,deviceId="device",informAt=wifiAt.plusSeconds(300),observedAt=wifiAt,
                associatedDeviceCount=4,associated2g=4,associated5g=0,qualityStatus=Quality.FRESH))
        }
        em.clear()
        assertEquals(1,counts.count())
        assertEquals(3,counts.findByDeviceIdAndSubscriptionIdAndObservedAt("device",1,wifiAt)!!.associatedDeviceCount)
    }
    @Test fun `identity history survives replacement`() {
        val at=Instant.parse("2026-08-30T15:00:00Z")
        links.saveAndFlush(IdentityLink(subscriptionId=1,kind="ONU",identityValue="old",validFrom=at,validTo=at.plusSeconds(300)))
        links.saveAndFlush(IdentityLink(subscriptionId=1,kind="ONU",identityValue="new",validFrom=at.plusSeconds(300)))
        em.clear()
        assertEquals("new",links.findBySubscriptionIdAndValidToIsNull(1).single().identityValue)
        assertEquals(2,links.count())
    }
    @Test fun `persistent coordinator row supports pessimistic locking`() {
        cursors.saveAndFlush(HealthCursor(cursorKey="worker"))
        assertNotNull(cursors.lock("worker"))
    }
    @Test fun `new domain persists UTC despite legacy Lima JDBC timezone`() {
        val at=Instant.parse("2026-08-30T15:00:00Z")
        val sample=optical.saveAndFlush(OpticalSample(subscriptionId=99,onuId=99,observedAt=at,collectedAt=at))
        val raw=em.createNativeQuery("select cast(observed_at as varchar) from olt_mgr_onu_optical_sample where id = :id")
            .setParameter("id",sample.id).singleResult.toString()
        assertTrue(raw.startsWith("2026-08-30 15:00:00"),raw)
        em.clear()
        assertEquals(at,optical.findById(sample.id!!).get().observedAt)
    }
}
