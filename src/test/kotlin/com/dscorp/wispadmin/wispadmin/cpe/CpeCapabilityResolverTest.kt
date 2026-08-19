package com.dscorp.wispadmin.wispadmin.cpe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CpeCapabilityResolverTest {

    private val resolver = CpeCapabilityResolver(
        listOf(VsolCpeCapabilityStrategy(), DefaultCpeCapabilityStrategy())
    )

    @Test
    fun `V-SOL ONU cannot write WAN by TR-069 but keeps wifi writable`() {
        val capabilities = resolver.resolve(
            CpeDeviceProfile(serialNumber = "HWTC15F5E946", vendor = "B46415", model = "V2804AX15T")
        )

        assertFalse(capabilities.canWriteWanViaTr069)
        assertTrue(capabilities.canWriteWanViaOmci)
        assertTrue(capabilities.canWriteWifiViaTr069)
        assertEquals(WanManagement.OLT_OMCI, capabilities.wanManagedBy)
    }

    @Test
    fun `manufacturer reported as V-SOL is detected regardless of model`() {
        val capabilities = resolver.resolve(
            CpeDeviceProfile(serialNumber = "HWTC15F5E946", vendor = "V-SOL", model = "XPON ONU")
        )

        assertFalse(capabilities.canWriteWanViaTr069)
        assertTrue(capabilities.canWriteWifiViaTr069)
    }

    @Test
    fun `Huawei ONU keeps full TR-069 capabilities`() {
        val capabilities = resolver.resolve(
            CpeDeviceProfile(serialNumber = "HWTC15F5CD86", vendor = "00259E", model = "EG8145V5")
        )

        assertTrue(capabilities.canWriteWanViaTr069)
        assertTrue(capabilities.canWriteWanViaOmci)
        assertTrue(capabilities.canWriteWifiViaTr069)
        assertEquals(WanManagement.TR069, capabilities.wanManagedBy)
    }

    @Test
    fun `unknown device falls back to full capabilities`() {
        val capabilities = resolver.resolve(
            CpeDeviceProfile(serialNumber = "UNKNOWNSN", vendor = null, model = null)
        )

        assertTrue(capabilities.canWriteWanViaTr069)
        assertTrue(capabilities.canWriteWifiViaTr069)
    }

    @Test
    fun `descriptor without ACS presence resolves to default capabilities`() {
        val capabilities = resolver.resolve(null)

        assertTrue(capabilities.canWriteWanViaTr069)
        assertTrue(capabilities.canWriteWifiViaTr069)
        assertEquals(WanManagement.TR069, capabilities.wanManagedBy)
    }
}
