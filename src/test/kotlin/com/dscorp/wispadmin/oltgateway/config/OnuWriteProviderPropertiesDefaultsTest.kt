package com.dscorp.wispadmin.oltgateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OnuWriteProviderPropertiesDefaultsTest {

    @Test
    fun `defaults route all ONU writes through the gateway`() {
        val props = OnuWriteProviderProperties()

        assertEquals(OnuWriteProvider.GATEWAY, props.authorize)
        assertEquals(OnuWriteProvider.GATEWAY, props.delete)
        assertEquals(OnuWriteProvider.GATEWAY, props.reboot)
        assertEquals(OnuWriteProvider.GATEWAY, props.move)
        assertFalse(props.authorizeShadow)
    }
}
