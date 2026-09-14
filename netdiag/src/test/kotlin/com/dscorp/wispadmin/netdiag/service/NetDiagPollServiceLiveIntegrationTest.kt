package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagDeviceDirectoryPort
import com.dscorp.wispadmin.routeros.Mk1LiveSupport
import com.dscorp.wispadmin.wispadmin.WispAdminApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [WispAdminApplication::class])
@ActiveProfiles("dev")
@TestPropertySource(
    properties = [
        "net.diag.enabled=true",
        "net.diag.api-key=dev-netdiag-key",
        "net.diag.poll.initial-delay-ms=600000",
        "net.diag.poll.interval-ms=600000",
        "spring.datasource.password=VFt!03w^P7r9UmIIr9S0TYi5DiW8\$bEQ",
        "olt.gateway.reachability.failure-threshold=1",
        "olt.gateway.reachability.backoff-ms=60000",
    ]
)
@Tag("live-mk1")
class NetDiagPollServiceLiveIntegrationTest {

    @Autowired
    private lateinit var pollService: NetDiagPollService

    @Autowired
    private lateinit var targetRepository: NetDiagTargetRepository

    @Autowired
    private lateinit var probeRunRepository: NetDiagProbeRunRepository

    @Autowired
    private lateinit var deviceDirectory: NetDiagDeviceDirectoryPort

    @Test
    fun `spring pollOne MK1 succeeds via REST`() {
        Mk1LiveSupport.requireCredentials()
        val target = targetRepository.findByEnabledTrue().firstOrNull { it.name == "MK1" }
            ?: error("MK1 target missing in net_diag_target")
        val deviceRef = deviceDirectory.findMikrotikDeviceRef(target.deviceRefId)
        assertNotNull(deviceRef)
        assertEquals("38.224.231.2", deviceRef!!.host)
        assertEquals(443, deviceRef.port)

        pollService.pollOne(target)

        val latest = probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(target.id!!).orElseThrow()
        assertEquals("SUCCESS", latest.status, latest.error)
    }
}
