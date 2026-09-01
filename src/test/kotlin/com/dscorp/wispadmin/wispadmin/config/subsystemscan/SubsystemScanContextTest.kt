package com.dscorp.wispadmin.wispadmin.config.subsystemscan

import com.dscorp.wispadmin.servicehealth.controller.ServiceHealthController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [SubsystemScanHarness::class])
@TestPropertySource(
    properties = [
        "gigafiber.subsystems.servicehealth.enabled=false",
        "spring.main.web-application-type=none"
    ]
)
class SubsystemScanContextTest {

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun serviceHealthControllerIsNotLoadedWhenDisabled() {
        assertEquals(0, context.getBeanNamesForType(ServiceHealthController::class.java).size)
    }
}
