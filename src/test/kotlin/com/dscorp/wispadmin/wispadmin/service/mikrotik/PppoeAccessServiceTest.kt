package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.PppoeProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PppoeAccessServiceTest {

    private lateinit var pppoeManager: PppoeManagerService

    private lateinit var cipher: CrmSecretCipher

    private lateinit var session: MikrotikSession

    private lateinit var service: PppoeAccessService

    @BeforeEach
    fun setUp() {
        pppoeManager = mockk(relaxed = true)
        cipher = mockk(relaxed = true)
        session = mockk(relaxed = true)
        service = PppoeAccessService(pppoeManager, cipher) { _, block -> block(session) }
    }

    @Test
    fun `ensureSecret ignora suscripciones que no son PPPOE_DYNAMIC`() {
        val subscription = subscription(AccessMode.STATIC_IP)

        val result = service.ensureSecret(subscription, device())

        assertFalse(result.created)
        assertNull(subscription.pppoeProvisionStatus)
        verify(exactly = 0) { pppoeManager.ensureSecret(any(), any(), any()) }
    }

    @Test
    fun `ensureSecret crea el secret y marca SECRET_CREATED`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        every { pppoeManager.ensureSecret(session, subscription, any()) } returns
            PppoeSecretResult(created = true, profile = "GF-200-200")

        val result = service.ensureSecret(subscription, device())

        assertTrue(result.successful)
        assertEquals("GF-200-200", subscription.pppoeProfile)
        assertEquals(PppoeProvisionStatus.SECRET_CREATED, subscription.pppoeProvisionStatus)
    }

    @Test
    fun `ensureSecret descifra la contrasena almacenada antes de enviarla a MikroTik`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            pppoePasswordEnc = "enc:v1:cifrado"
        }
        every { cipher.looksEncrypted("enc:v1:cifrado") } returns true
        every { cipher.decrypt("enc:v1:cifrado") } returns "secreto123"
        every { pppoeManager.ensureSecret(session, subscription, "secreto123") } returns
            PppoeSecretResult(created = true, profile = "GF-200-200")

        service.ensureSecret(subscription, device())

        verify { pppoeManager.ensureSecret(session, subscription, "secreto123") }
    }

    @Test
    fun `decryptedPassword solo aplica a PPPOE_DYNAMIC con secreto cifrado`() {
        val dynamic = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            pppoePasswordEnc = "enc:v1:cifrado"
        }
        every { cipher.looksEncrypted("enc:v1:cifrado") } returns true
        every { cipher.decrypt("enc:v1:cifrado") } returns "secreto123"

        assertEquals("secreto123", service.decryptedPassword(dynamic))
        assertNull(service.decryptedPassword(subscription(AccessMode.STATIC_IP)))
    }

    @Test
    fun `ensureSecret no descifra valores que no estan cifrados`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            pppoePasswordEnc = "texto-plano"
        }
        every { cipher.looksEncrypted("texto-plano") } returns false
        every { pppoeManager.ensureSecret(session, subscription, null) } returns
            PppoeSecretResult(created = true)

        service.ensureSecret(subscription, device())

        verify(exactly = 0) { cipher.decrypt(any()) }
        verify { pppoeManager.ensureSecret(session, subscription, null) }
    }

    @Test
    fun `ensureSecret marca FAILED cuando MikroTik devuelve error`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        every { pppoeManager.ensureSecret(session, subscription, any()) } returns
            PppoeSecretResult(error = "no such item")

        val result = service.ensureSecret(subscription, device())

        assertFalse(result.successful)
        assertEquals("no such item", result.error)
        assertEquals(PppoeProvisionStatus.FAILED, subscription.pppoeProvisionStatus)
    }

    @Test
    fun `ensureSecret no propaga MikrotikException y marca FAILED`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        service = PppoeAccessService(pppoeManager, cipher) { _, _ ->
            throw MikrotikCommandException("Remote host terminated the handshake")
        }

        val result = service.ensureSecret(subscription, device())

        assertFalse(result.successful)
        assertEquals("Remote host terminated the handshake", result.error)
        assertEquals(PppoeProvisionStatus.FAILED, subscription.pppoeProvisionStatus)
    }

    @Test
    fun `ensureSecret no toca MikroTik en modo mock`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        service = PppoeAccessService(pppoeManager, cipher, mockMode = { true }) { _, block -> block(session) }

        val result = service.ensureSecret(subscription, device())

        assertTrue(result.successful)
        assertNull(result.error)
        verify(exactly = 0) { pppoeManager.ensureSecret(any(), any(), any()) }
    }

    @Test
    fun `cut mueve el secret a GF-CORTE y expulsa la sesion`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        every { pppoeManager.applyProfile(session, "gf4321", PppoeProfileCatalog.CUT_PROFILE) } returns true

        val applied = service.cut(subscription, device())

        assertTrue(applied)
        assertEquals(PppoeProvisionStatus.CUT, subscription.pppoeProvisionStatus)
        verifyOrder {
            pppoeManager.applyProfile(session, "gf4321", PppoeProfileCatalog.CUT_PROFILE)
            pppoeManager.kickSession(session, "gf4321")
        }
    }

    @Test
    fun `cut aplica el perfil de corte tambien a PPPOE_FIXED`() {
        val subscription = subscription(AccessMode.PPPOE_FIXED)
        every { pppoeManager.applyProfile(session, "gf4321", PppoeProfileCatalog.CUT_PROFILE) } returns true

        assertTrue(service.cut(subscription, device()))
        assertEquals(PppoeProvisionStatus.CUT, subscription.pppoeProvisionStatus)
        verify { pppoeManager.applyProfile(session, "gf4321", PppoeProfileCatalog.CUT_PROFILE) }
    }

    @Test
    fun `restore aplica el perfil del plan a PPPOE_FIXED`() {
        val subscription = subscription(AccessMode.PPPOE_FIXED).apply {
            pppoeProvisionStatus = PppoeProvisionStatus.CUT
        }
        every { pppoeManager.applyProfile(session, "gf4321", "GF-200-200") } returns true

        assertTrue(service.restore(subscription, device()))
        assertEquals("GF-200-200", subscription.pppoeProfile)
    }

    @Test
    fun `cut ignora suscripciones STATIC_IP`() {
        val subscription = subscription(AccessMode.STATIC_IP)

        assertFalse(service.cut(subscription, device()))
        verify(exactly = 0) { pppoeManager.applyProfile(any(), any(), any()) }
        verify(exactly = 0) { pppoeManager.kickSession(any(), any()) }
    }

    @Test
    fun `cut no expulsa la sesion si el secret no existe`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        every { pppoeManager.applyProfile(session, "gf4321", PppoeProfileCatalog.CUT_PROFILE) } returns false

        assertFalse(service.cut(subscription, device()))
        verify(exactly = 0) { pppoeManager.kickSession(any(), any()) }
    }

    @Test
    fun `restore devuelve el perfil del plan y expulsa la sesion`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            pppoeProvisionStatus = PppoeProvisionStatus.CUT
        }
        every { pppoeManager.applyProfile(session, "gf4321", "GF-200-200") } returns true

        val applied = service.restore(subscription, device())

        assertTrue(applied)
        assertEquals("GF-200-200", subscription.pppoeProfile)
        assertEquals(PppoeProvisionStatus.SECRET_CREATED, subscription.pppoeProvisionStatus)
        verifyOrder {
            pppoeManager.applyProfile(session, "gf4321", "GF-200-200")
            pppoeManager.kickSession(session, "gf4321")
        }
    }

    @Test
    fun `restore falla cuando el plan no tiene velocidades`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            plan = Plan(id = 1, name = "cable", downloadSpeed = 0, uploadSpeed = 0)
        }

        assertFalse(service.restore(subscription, device()))
        verify(exactly = 0) { pppoeManager.applyProfile(any(), any(), any()) }
    }

    @Test
    fun `applyPlanProfile actualiza el perfil sin expulsar la sesion`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            plan = Plan(id = 55, name = "f300", downloadSpeed = 300, uploadSpeed = 300)
        }
        every { pppoeManager.applyProfile(session, "gf4321", "GF-300-300") } returns true

        assertTrue(service.applyPlanProfile(subscription, device()))
        assertEquals("GF-300-300", subscription.pppoeProfile)
        verify(exactly = 0) { pppoeManager.kickSession(any(), any()) }
    }

    @Test
    fun `applyPlanProfile no toca el perfil de una suscripcion cortada`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC).apply {
            pppoeProvisionStatus = PppoeProvisionStatus.CUT
        }

        assertFalse(service.applyPlanProfile(subscription, device()))
        verify(exactly = 0) { pppoeManager.applyProfile(any(), any(), any()) }
    }

    @Test
    fun `cut no propaga MikrotikException`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC)
        service = PppoeAccessService(pppoeManager, cipher) { _, _ ->
            throw MikrotikCommandException("timeout")
        }

        assertFalse(service.cut(subscription, device()))
    }

    private fun subscription(mode: AccessMode) = Subscription(
        id = 4321,
        firstName = "Juan",
        lastName = "Perez",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply {
        accessMode = mode
        pppoeUsername = "gf4321"
        plan = Plan(id = 54, name = "f200", downloadSpeed = 200, uploadSpeed = 200)
    }

    private fun device() = NetworkDevice(
        id = 8,
        name = "MK8",
        ipAddress = "38.224.231.4",
        networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
        vlanId = 100
    )
}
