package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:subscription_acs;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ]
)
class SubscriptionAcsJpaTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [SubscriptionAcs::class])
    @EnableJpaRepositories(basePackageClasses = [SubscriptionAcsRepository::class])
    class TestApp

    @Autowired
    private lateinit var repository: SubscriptionAcsRepository

    @Test
    fun `persists and reloads subscription_acs 1 to 1 row`() {
        val now = LocalDateTime.of(2026, 8, 20, 15, 30)
        val saved = repository.saveAndFlush(
            SubscriptionAcs(
                subscriptionId = 101,
                genieacsDeviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                serialSuffix = "31C0B6",
                smartoltSerial = "VSOL0031C0B6",
                provisionStatus = Tr069ProvisionStatus.COMPLETE,
                lastInformAt = now,
                productClass = "V2804AX15T",
                oui = "B46415",
                manufacturer = "VSOL",
                connectionRequestUrl = "http://192.168.123.4:7547/tr069",
                wanIpCache = "192.168.123.4",
                ssid24 = "acs2g",
                ssid5 = "acs5g",
                softwareVersion = "V1.0",
                hardwareVersion = "V1.1",
                lastBootAt = now.minusMinutes(5),
                lastTaskId = "task-1",
                lastTaskStatus = "accepted",
                lastTaskAt = now,
                provisionedAt = now,
                updatedAt = now,
            )
        )

        val loaded = repository.findById(101).orElseThrow()
        assertEquals(saved.subscriptionId, loaded.subscriptionId)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", loaded.genieacsDeviceId)
        assertEquals("31C0B6", loaded.serialSuffix)
        assertEquals(Tr069ProvisionStatus.COMPLETE, loaded.provisionStatus)
        assertEquals("acs2g", loaded.ssid24)
        assertTrue(loaded.toDto().tr069RequiresManualConfig.not())
        assertEquals("task-1", loaded.toDto().lastTaskId)
    }
}
