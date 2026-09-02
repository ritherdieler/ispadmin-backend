package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProvider
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProviderProperties
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.response.Onu as SmartOltOnuLegacy
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.service.OltService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class OnuWriteRouterTest {

    private val oltService = mockk<OltService>(relaxed = true)
    private val facade = mockk<OltManagerFacade>()
    private val providers = OnuWriteProviderProperties()
    private lateinit var router: OnuWriteRouter

    private val authorizeRequest = AuthorizeOnuFormDto(
        olt_id = "gigafiber-ma5608t",
        board = "0",
        port = "2",
        sn = "4857544311E70E9A",
        vlan = "100",
        onu_type = "HG8245H",
        zone = "ZonaA",
        name = "nuevo",
        onu_mode = "routing",
        custom_profile = "Generic_1"
    )

    private val gatewayProperties = OltGatewayProperties().apply { oltId = "gigafiber-ma5608t" }

    private fun provider(bean: OltManagerFacade?): ObjectProvider<OltManagerFacade> =
        mockk<ObjectProvider<OltManagerFacade>>().also {
            every { it.getIfAvailable() } returns bean
        }

    private fun propertiesProvider(): ObjectProvider<OltGatewayProperties> =
        mockk<ObjectProvider<OltGatewayProperties>>().also {
            every { it.getIfAvailable() } returns gatewayProperties
        }

    @BeforeEach
    fun setUp() {
        router = OnuWriteRouter(oltService, provider(facade), providers, propertiesProvider())
    }

    private val moveRequest = MoveOnuRequest(subscriptionId = 1, newNapBoxId = 2)
    private val legacyOnu = Onu(sn = "4857544311E70E9A", board = "0", port = "2", onu = "5")
    private val newNapBox = NapBox(id = 2, code = "NAP-2", oltId = 1, oltBoard = 1, oltPort = 0)

    @Test
    fun `por defecto todas las escrituras van al gateway`() {
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(status = true)
        every { facade.deleteOnu(any()) } returns SmartOltActionResponseDto(status = true)
        every { facade.rebootOnu(any()) } returns SmartOltActionResponseDto(status = true)
        every { facade.moveOnu(any(), any()) } returns SmartOltActionResponseDto(status = true)

        router.authorize(authorizeRequest)
        router.delete("gigafiber-ma5608t_0_2_5")
        router.reboot("gigafiber-ma5608t_0_2_5")
        router.move(moveRequest, legacyOnu, newNapBox)

        verify { facade.authorizeOnu(authorizeRequest) }
        verify { facade.deleteOnu("gigafiber-ma5608t_0_2_5") }
        verify { facade.rebootOnu("gigafiber-ma5608t_0_2_5") }
        verify { facade.moveOnu(any(), any()) }
        verify(exactly = 0) { oltService.authorizeOnuInSmartOltWidthPostMethod(any()) }
        verify(exactly = 0) { oltService.deleteOnu(any()) }
        verify(exactly = 0) { oltService.rebootOnu(any()) }
        verify(exactly = 0) { oltService.moveOnu(any(), any(), any()) }
    }

    @Test
    fun `reboot puede conmutarse al gateway sin arrastrar las demas operaciones`() {
        providers.authorize = OnuWriteProvider.SMARTOLT
        providers.delete = OnuWriteProvider.SMARTOLT
        providers.move = OnuWriteProvider.SMARTOLT
        providers.reboot = OnuWriteProvider.GATEWAY
        every { facade.rebootOnu("gigafiber-ma5608t_1_0_5") } returns
            SmartOltActionResponseDto(status = true, unique_external_id = "gigafiber-ma5608t_1_0_5")

        val response = router.reboot("gigafiber-ma5608t_1_0_5")

        assertTrue(response.status)
        verify { facade.rebootOnu("gigafiber-ma5608t_1_0_5") }
        verify(exactly = 0) { oltService.rebootOnu(any()) }

        router.delete("184")
        verify { oltService.deleteOnu("184") }
    }

    @Test
    fun `delete y move pueden conmutarse al gateway`() {
        providers.delete = OnuWriteProvider.GATEWAY
        providers.move = OnuWriteProvider.GATEWAY
        every { facade.deleteOnu(any()) } returns SmartOltActionResponseDto(status = true)
        every { facade.moveOnu(any(), any()) } returns SmartOltActionResponseDto(status = true)

        router.delete("gigafiber-ma5608t_1_0_5")
        router.move(moveRequest, legacyOnu, newNapBox)

        verify { facade.deleteOnu("gigafiber-ma5608t_1_0_5") }
        verify {
            facade.moveOnu(
                "4857544311E70E9A",
                MoveOnuFormDto(olt_id = "1", board = "1", port = "0")
            )
        }
        verify(exactly = 0) { oltService.deleteOnu(any()) }
        verify(exactly = 0) { oltService.moveOnu(any(), any(), any()) }
    }

    @Test
    fun `authorize por gateway devuelve el identificador propio`() {
        providers.authorize = OnuWriteProvider.GATEWAY
        every { facade.authorizeOnu(authorizeRequest) } returns
            SmartOltActionResponseDto(status = true, unique_external_id = "gigafiber-ma5608t_0_2_7")

        val response = router.authorize(authorizeRequest)

        assertEquals("gigafiber-ma5608t_0_2_7", response.unique_external_id)
        verify(exactly = 0) { oltService.authorizeOnuInSmartOltWidthPostMethod(any()) }
    }

    @Test
    fun `si el gateway no esta disponible las escrituras caen a SmartOLT`() {
        providers.reboot = OnuWriteProvider.GATEWAY
        router = OnuWriteRouter(oltService, provider(null), providers, propertiesProvider())

        router.reboot("184")

        verify { oltService.rebootOnu("184") }
    }

    @Test
    fun `el modo sombra aplica por SmartOLT y registra lo que habria hecho el gateway`() {
        providers.authorize = OnuWriteProvider.SMARTOLT
        providers.authorizeShadow = true
        every { oltService.getOnuBySn("4857544311E70E9A") } returns OnuBySnResponse(
            onus = listOf(
                SmartOltOnuLegacy().copy(board = "0", port = "2", onu = "9", unique_external_id = "184")
            ),
            response_code = "200",
            status = true
        )
        every { facade.recordAuthorizeShadow(any(), any()) } returns true

        router.authorize(authorizeRequest)

        verify { oltService.authorizeOnuInSmartOltWidthPostMethod(any()) }
        verify {
            facade.recordAuthorizeShadow(
                authorizeRequest,
                AppliedAuthorization(board = 0, port = 2, ontId = 9, externalId = "184")
            )
        }
        verify(exactly = 0) { facade.authorizeOnu(any()) }
    }

    @Test
    fun `el modo sombra no rompe el alta si no se puede leer lo aplicado`() {
        providers.authorize = OnuWriteProvider.SMARTOLT
        providers.authorizeShadow = true
        every { oltService.getOnuBySn(any()) } throws IllegalStateException("SmartOLT caido")
        every { facade.recordAuthorizeShadow(any(), null) } returns true

        val response = router.authorize(authorizeRequest)

        assertTrue(response.status)
        verify { facade.recordAuthorizeShadow(authorizeRequest, null) }
    }

    @Test
    fun `el modo sombra no rompe el alta si falla el registro`() {
        providers.authorize = OnuWriteProvider.SMARTOLT
        providers.authorizeShadow = true
        every { oltService.getOnuBySn(any()) } returns OnuBySnResponse(emptyList(), "404", false)
        every { facade.recordAuthorizeShadow(any(), any()) } throws IllegalStateException("audit caido")

        val response = router.authorize(authorizeRequest)

        assertTrue(response.status)
    }

    @Test
    fun `borrar por SN con SmartOLT usa el identificador que devuelve SmartOLT`() {
        providers.delete = OnuWriteProvider.SMARTOLT
        every { oltService.getOnuBySn("4857544311E70E9A") } returns OnuBySnResponse(
            onus = listOf(SmartOltOnuLegacy().copy(unique_external_id = "184")),
            response_code = "200",
            status = true
        )

        router.deleteBySn("4857544311E70E9A")

        verify { oltService.deleteOnu("184") }
    }

    @Test
    fun `borrar por SN con gateway usa el identificador propio`() {
        providers.delete = OnuWriteProvider.GATEWAY
        every { facade.getOnusDetailsBySn("4857544311E70E9A") } returns SmartOltOnuBySnResponseDto(
            onus = listOf(SmartOltOnuDto(sn = "4857544311E70E9A", unique_external_id = "gigafiber-ma5608t_1_0_5")),
            response_code = "200",
            status = true
        )
        every { facade.deleteOnu(any()) } returns SmartOltActionResponseDto(status = true)

        router.deleteBySn("4857544311E70E9A")

        verify { facade.deleteOnu("gigafiber-ma5608t_1_0_5") }
        verify(exactly = 0) { oltService.getOnuBySn(any()) }
    }

    @Test
    fun `reiniciar por SN falla con mensaje claro si nadie conoce la ONU`() {
        providers.reboot = OnuWriteProvider.SMARTOLT
        every { oltService.getOnuBySn("missing") } returns OnuBySnResponse(emptyList(), "404", false)

        val error = assertThrows(IllegalArgumentException::class.java) { router.rebootBySn("missing") }

        assertEquals("No se encontró la ONU en SmartOLT para el serial indicado", error.message)
    }

    @Test
    fun `reiniciar por SN falla si la ONU no tiene identificador externo`() {
        providers.reboot = OnuWriteProvider.SMARTOLT
        every { oltService.getOnuBySn("4857544311E70E9A") } returns OnuBySnResponse(
            onus = listOf(SmartOltOnuLegacy()),
            response_code = "200",
            status = true
        )

        assertThrows(IllegalStateException::class.java) { router.rebootBySn("4857544311E70E9A") }
    }

    @Test
    fun `una operacion por SmartOLT traduce el identificador propio al de SmartOLT`() {
        providers.delete = OnuWriteProvider.SMARTOLT
        every { facade.findSnByExternalId("gigafiber-ma5608t_1_0_5") } returns "4857544311E70E9A"
        every { oltService.getOnuBySn("4857544311E70E9A") } returns OnuBySnResponse(
            onus = listOf(SmartOltOnuLegacy().copy(unique_external_id = "184")),
            response_code = "200",
            status = true
        )

        router.delete("gigafiber-ma5608t_1_0_5")

        verify { oltService.deleteOnu("184") }
    }

    @Test
    fun `una operacion por SmartOLT no traduce un identificador que ya es de SmartOLT`() {
        providers.reboot = OnuWriteProvider.SMARTOLT
        router.reboot("184")

        verify { oltService.rebootOnu("184") }
        verify(exactly = 0) { facade.findSnByExternalId(any()) }
    }

    @Test
    fun `el modo sombra no se ejecuta cuando authorize ya va por gateway`() {
        providers.authorize = OnuWriteProvider.GATEWAY
        providers.authorizeShadow = true
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(status = true)

        router.authorize(authorizeRequest)

        verify(exactly = 0) { facade.recordAuthorizeShadow(any(), any()) }
    }
}
