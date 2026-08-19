package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.response.Onu
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.util.HttpClient
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.util.MultiValueMap

class RealOltServiceTest {

    private val service = RealOltService(baseUrl = BASE_URL, apiKey = API_KEY)

    @BeforeEach
    fun setUp() {
        mockkObject(HttpClient)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(HttpClient)
    }

    @Test
    fun `updateOnuWanConfig envia set_wan_mode estatico con los datos de red`() {
        givenOnuFound()
        every { HttpClient.post(BASE_URL, API_KEY, any<String>(), any(), Any::class.java) } returns Any()
        val body = slot<Any>()

        val result = service.updateOnuWanConfig(
            sn = SN,
            vlan = 100,
            ip = "192.168.30.50",
            mask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns1 = "8.8.8.8",
            dns2 = "8.8.4.4",
        )

        assertTrue(result.applied)
        assertEquals(EXTERNAL_ID, result.uniqueExternalId)
        assertTrue(result.warnings.isEmpty())

        verify(exactly = 1) {
            HttpClient.post(BASE_URL, API_KEY, "onu/set_wan_mode/$EXTERNAL_ID", capture(body), Any::class.java)
        }
        val form = body.captured.asForm()
        assertEquals("static", form.getFirst("wan_mode"))
        assertEquals("192.168.30.50", form.getFirst("ip_address"))
        assertEquals("255.255.255.0", form.getFirst("subnet_mask"))
        assertEquals("192.168.30.1", form.getFirst("default_gateway"))
        assertEquals("8.8.8.8", form.getFirst("dns1"))
        assertEquals("8.8.4.4", form.getFirst("dns2"))
        assertEquals("100", form.getFirst("vlan"))
    }

    @Test
    fun `updateOnuWanConfig usa update_vlan cuando solo cambia la VLAN`() {
        givenOnuFound()
        every { HttpClient.post(BASE_URL, API_KEY, any<String>(), any(), Any::class.java) } returns Any()
        val body = slot<Any>()

        val result = service.updateOnuWanConfig(
            sn = SN,
            vlan = 200,
            ip = null,
            mask = null,
            gateway = null,
            dns1 = null,
            dns2 = null,
        )

        assertTrue(result.applied)
        verify(exactly = 1) {
            HttpClient.post(BASE_URL, API_KEY, "onu/update_vlan/$EXTERNAL_ID", capture(body), Any::class.java)
        }
        assertEquals("200", body.captured.asForm().getFirst("vlan"))
        verify(exactly = 0) {
            HttpClient.post(BASE_URL, API_KEY, "onu/set_wan_mode/$EXTERNAL_ID", any(), Any::class.java)
        }
    }

    @Test
    fun `updateOnuWanConfig avisa cuando la OLT no conoce el serial`() {
        every {
            HttpClient.get(BASE_URL, API_KEY, "onu/get_onus_details_by_sn/$SN", OnuBySnResponse::class.java)
        } returns OnuBySnResponse(onus = emptyList(), response_code = "404", status = false)

        val result = service.updateOnuWanConfig(SN, 100, "192.168.30.50", null, null, null, null)

        assertFalse(result.applied)
        assertEquals(1, result.warnings.size)
        verify(exactly = 0) { HttpClient.post(BASE_URL, API_KEY, any<String>(), any(), Any::class.java) }
    }

    @Test
    fun `updateOnuWanConfig convierte el fallo de la OLT en aviso sin propagar la excepcion`() {
        givenOnuFound()
        every {
            HttpClient.post(BASE_URL, API_KEY, any<String>(), any(), Any::class.java)
        } throws RuntimeException("SmartOLT respondió 500")

        val result = service.updateOnuWanConfig(SN, null, "192.168.30.50", "255.255.255.0", null, null, null)

        assertFalse(result.applied)
        assertTrue(result.warnings.any { it.contains("SmartOLT respondió 500") })
    }

    @Test
    fun `updateOnuWanConfig no llama a la OLT cuando no hay datos que aplicar`() {
        val result = service.updateOnuWanConfig(SN, null, null, null, null, null, null)

        assertFalse(result.applied)
        verify(exactly = 0) { HttpClient.get(BASE_URL, API_KEY, any<String>(), OnuBySnResponse::class.java) }
        verify(exactly = 0) { HttpClient.post(BASE_URL, API_KEY, any<String>(), any(), Any::class.java) }
    }

    private fun givenOnuFound() {
        every {
            HttpClient.get(BASE_URL, API_KEY, "onu/get_onus_details_by_sn/$SN", OnuBySnResponse::class.java)
        } returns OnuBySnResponse(
            onus = listOf(Onu().copy(sn = SN, unique_external_id = EXTERNAL_ID)),
            response_code = "200",
            status = true,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.asForm(): MultiValueMap<String, Any> = this as MultiValueMap<String, Any>

    private companion object {
        const val BASE_URL = "http://olt.local/api/"
        const val API_KEY = "test-key"
        const val SN = "HWTC15F5E946"
        const val EXTERNAL_ID = "1_1_0_5"
    }
}
