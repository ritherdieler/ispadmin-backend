package com.dscorp.wispadmin.wispadmin.config

import io.swagger.v3.oas.models.security.SecurityScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiConfigTest {

    @Test
    fun `openAPI define titulo WispAdmin y esquema X-Olt-Gateway-Key`() {
        val openApi = OpenApiConfig().wispAdminOpenApi()

        assertEquals("WispAdmin / ISP Admin API", openApi.info.title)
        assertTrue(openApi.info.description.contains("OLT Gateway"))
        val scheme = openApi.components.securitySchemes[OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME]
        assertNotNull(scheme)
        assertEquals(SecurityScheme.Type.APIKEY, scheme!!.type)
        assertEquals(SecurityScheme.In.HEADER, scheme.`in`)
        assertEquals("X-Olt-Gateway-Key", scheme.name)
    }
}
