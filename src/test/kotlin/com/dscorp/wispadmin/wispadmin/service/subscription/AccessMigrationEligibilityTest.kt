package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessMigrationEligibilityTest {

    @Test
    fun `V2804 with dedicated management in provisioning-255 is eligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.50", sn = "VSOL0031C0B6"),
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.253.40:7547/"),
        )
        assertEquals(AccessMigrationEligibilityResult.Eligible, result)
    }

    @Test
    fun `V2804 lab with dedicated ACS on vlan1000 10_20 is eligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.100.40", sn = "VSOL0031C0B6"),
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://10.20.0.2:7547/tr069"),
        )
        assertEquals(AccessMigrationEligibilityResult.Eligible, result)
    }

    @Test
    fun `F6600R with dedicated management is eligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.80", sn = "ZTEGDC47BFFD"),
            plan = fiberPlan(300, 300),
            cpeModel = AccessMigrationCpeModel("F6600R", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.254.12:7547/"),
        )
        assertTrue(result.eligible)
    }

    @Test
    fun `IGD without PPP path is ineligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.50", sn = "IGD123456"),
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("IGD", hasPppPath = false),
            acs = AccessMigrationAcsState("http://192.168.253.40:7547/"),
        )
        assertEquals(
            "El modelo IGD no declara ruta WANPPP",
            (result as AccessMigrationEligibilityResult.Ineligible).reason,
        )
    }

    @Test
    fun `CPE managed by its own service IP is ineligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.50", sn = "VSOL0031C0B6"),
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.1.50:7547/"),
        )
        assertEquals(
            "El CPE se gestiona por la IP de servicio",
            (result as AccessMigrationEligibilityResult.Ineligible).reason,
        )
    }

    @Test
    fun `WIRELESS is ineligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.50", sn = "VSOL0031C0B6").apply {
                installationType = InstallationType.WIRELESS
            },
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.253.40:7547/"),
        )
        assertEquals("Solo FIBER es migrable", (result as AccessMigrationEligibilityResult.Ineligible).reason)
    }

    @Test
    fun `invalid IP is ineligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "n/a", sn = "VSOL0031C0B6"),
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.253.40:7547/"),
        )
        assertEquals(
            "La IP de servicio no es válida",
            (result as AccessMigrationEligibilityResult.Ineligible).reason,
        )
    }

    @Test
    fun `TV only is ineligible`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.50", sn = "VSOL0031C0B6").apply {
                installationType = InstallationType.ONLY_TV_FIBER
            },
            plan = fiberPlan(200, 200),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.253.40:7547/"),
        )
        assertEquals("Solo FIBER es migrable", (result as AccessMigrationEligibilityResult.Ineligible).reason)
    }

    @Test
    fun `plan with upload zero cannot derive a profile`() {
        val result = AccessMigrationEligibility.evaluate(
            subscription = fiberStatic(ip = "192.168.1.50", sn = "VSOL0031C0B6"),
            plan = Plan(id = 9, name = "PLAZA AUTOSERVICIO", downloadSpeed = 100, uploadSpeed = 0, type = InstallationType.FIBER),
            cpeModel = AccessMigrationCpeModel("V2804AX15T", hasPppPath = true),
            acs = AccessMigrationAcsState("http://192.168.253.40:7547/"),
        )
        assertEquals(
            "El plan no tiene velocidades para derivar un perfil PPPoE",
            (result as AccessMigrationEligibilityResult.Ineligible).reason,
        )
    }

    @Test
    fun `two WAN on different slots must abort`() {
        assertEquals(
            true,
            AccessMigrationWanGuard.wouldLeaveTwoActiveWans(
                wanIpPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
                pppPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1",
            ),
        )
    }

    @Test
    fun `empty paths from vparams layout do not abort`() {
        assertEquals(
            false,
            AccessMigrationWanGuard.wouldLeaveTwoActiveWans(wanIpPath = null, pppPath = null),
        )
        assertEquals(
            false,
            AccessMigrationWanGuard.wouldLeaveTwoActiveWans(wanIpPath = "", pppPath = "  "),
        )
    }

    @Test
    fun `shared slot returns the WANIP path to disable`() {
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            AccessMigrationWanGuard.replacedClientWanIpPath(
                wanIpPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
                pppPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2",
            ),
        )
    }

    private fun fiberStatic(ip: String, sn: String) = Subscription(
        id = 1001,
        firstName = "Ana",
        lastName = "Fiber",
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = ServiceStatus.ACTIVE,
        installationType = InstallationType.FIBER,
    ).apply {
        this.ip = ip
        accessMode = AccessMode.STATIC_IP
        fiberOnuSn = sn
    }

    private fun fiberPlan(download: Int, upload: Int) = Plan(
        id = 54,
        name = "FIBER $download",
        price = 80.0,
        downloadSpeed = download,
        uploadSpeed = upload,
        type = InstallationType.FIBER,
    )
}
