package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069PppoeProfileTest {

    private val stagingWan =
        "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1"

    private val clientPpp =
        "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2"

    private fun profile(clientPppPath: String? = clientPpp) = Tr069ModelProfile(
        productClass = "F6600R",
        wanIpConnectionPath = stagingWan,
        wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
        wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
        clientWanIpConnectionPath =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
        clientWanPppConnectionPath = clientPppPath,
        clientVlanParameters = listOf(
            Tr069VlanParameterSpec(
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.X_ZTE-COM_VLANID"
            ),
        ),
    )

    @Test
    fun `un perfil sin ruta PPP no soporta PPPoE`() {
        assertFalse(profile(clientPppPath = null).supportsPppoe())
    }

    @Test
    fun `un perfil con ruta PPP soporta PPPoE`() {
        assertTrue(profile().supportsPppoe())
    }

    @Test
    fun `forClientPppoeWan apunta la WAN de abonado a WANPPPConnection`() {
        val client = profile().forClientPppoeWan()

        assertEquals(clientPpp, client.wanIpConnectionPath)
        assertNull(client.wanGponLinkConfigPath)
        assertEquals(1, client.wanConnectionDeviceIndex)
    }

    @Test
    fun `forClientPppoeWan devuelve null si el modelo no declara ruta PPP`() {
        assertNull(profile(clientPppPath = null).forClientPppoeWanOrNull())
    }

    @Test
    fun `la instancia y el padre WCD se resuelven tambien sobre WANPPPConnection`() {
        val client = profile().forClientPppoeWan()

        assertEquals(2, client.wanIpInstanceIndex())
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice",
            client.wcdParentPath()
        )
    }

    @Test
    fun `el segmento de conexion distingue WANPPPConnection de WANIPConnection`() {
        assertEquals("WANPPPConnection", profile().forClientPppoeWan().wanConnectionSegment())
        assertEquals("WANIPConnection", profile().forClientInternetWan(2).wanConnectionSegment())
    }

    @Test
    fun `la instancia de abonado se resuelve sobre el segmento del perfil`() {
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection",
            profile().forClientPppoeWan().wanConnectionInstanceParentPath()
        )
    }

    @Test
    fun `los parametros PPPoE llevan credenciales tipo y VLAN`() {
        val values = profile().forClientPppoeWan()
            .buildClientPppoeWanParameterValues(
                username = "gf4321",
                password = "secreto123",
                vlanId = 100,
                connectionName = "GIGAFIBER-4321",
            )

        val byPath = values.associate { it.path to it.value }
        assertEquals("gf4321", byPath["$clientPpp.Username"])
        assertEquals("secreto123", byPath["$clientPpp.Password"])
        assertEquals("IP_Routed", byPath["$clientPpp.ConnectionType"])
        assertEquals("true", byPath["$clientPpp.Enable"])
        assertEquals("true", byPath["$clientPpp.NATEnabled"])
        assertEquals("GIGAFIBER-4321", byPath["$clientPpp.Name"])
        assertEquals("100", byPath["$clientPpp.X_ZTE-COM_VLANID"])
    }

    @Test
    fun `la WAN de IP estatica del mismo slot se apaga al montar la PPPoE`() {
        val staticWan =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2"
        val values = profile().forClientPppoeWan().buildClientPppoeWanParameterValues(
            username = "gf4321",
            password = "secreto123",
            vlanId = 100,
            connectionName = "GIGAFIBER-4321",
            replacedWanIpPath = staticWan,
        )

        val byPath = values.associate { it.path to it.value }
        assertEquals("false", byPath["$staticWan.Enable"])
        assertEquals("true", byPath["$clientPpp.Enable"])
        assertTrue(
            values.none { it.path.startsWith("$staticWan.") && !it.path.endsWith(".Enable") },
            "de la WAN reemplazada solo se toca Enable: ${values.map { it.path }}",
        )
    }

    @Test
    fun `sin WAN reemplazada no se emite ningun parametro extra`() {
        val values = profile().forClientPppoeWan()
            .buildClientPppoeWanParameterValues("gf4321", "secreto123", 100, "GIGAFIBER-4321")

        assertTrue(values.all { it.path.startsWith("$clientPpp.") }, values.map { it.path }.toString())
    }

    @Test
    fun `los parametros PPPoE nunca fijan IP estatica ni gateway`() {
        val values = profile().forClientPppoeWan()
            .buildClientPppoeWanParameterValues("gf4321", "secreto123", 100, "GIGAFIBER-4321")

        val paths = values.map { it.path }
        assertFalse(paths.any { it.endsWith(".ExternalIPAddress") })
        assertFalse(paths.any { it.endsWith(".DefaultGateway") })
        assertFalse(paths.any { it.endsWith(".AddressingType") })
        assertFalse(paths.any { it.endsWith(".SubnetMask") })
    }

    @Test
    fun `sin VLAN de cliente la PPPoE reusa la del modelo reapuntada al slot PPP`() {
        val vsol = Tr069ModelProfile(
            productClass = "V2804AX15T",
            wanIpConnectionPath =
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wanGponLinkConfigPath =
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_CT-COM_WANGponLinkConfig",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            vlanParameters = listOf(
                Tr069VlanParameterSpec(
                    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANID"
                ),
                Tr069VlanParameterSpec(
                    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_CT-COM_WANGponLinkConfig.VLANIDMark"
                ),
            ),
            clientWanPppConnectionPath =
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1",
        )

        val paths = vsol.forClientPppoeWan().vlanParameters.map { it.path }

        assertEquals(
            listOf(
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1.X_ZTE-COM_VLANID",
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark",
            ),
            paths,
        )
    }

    @Test
    fun `la VLAN de cliente declarada gana sobre la del modelo`() {
        assertEquals(
            listOf(
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.X_ZTE-COM_VLANID"
            ),
            profile().forClientPppoeWan().vlanParameters.map { it.path },
        )
    }

    @Test
    fun `la ruta de estado de conexion apunta a la WAN activa`() {
        assertEquals(
            "$clientPpp.ConnectionStatus",
            profile().forClientPppoeWan().connectionStatusPath()
        )
    }

    @Test
    fun `un request es PPPoE solo con username y password`() {
        assertTrue(request(username = "gf4321", password = "secreto123").usesPppoe())
        assertFalse(request(username = "gf4321", password = null).usesPppoe())
        assertFalse(request(username = null, password = "secreto123").usesPppoe())
        assertFalse(request(username = "  ", password = "secreto123").usesPppoe())
        assertFalse(request(username = null, password = null).usesPppoe())
    }

    @Test
    fun `en PPPoE basta cualquier IP observada para dar la WAN por buena`() {
        assertTrue(Tr069WanVerification.ipSatisfied("", "10.64.3.7", pppoe = true))
        assertFalse(Tr069WanVerification.ipSatisfied("", null, pppoe = true))
        assertFalse(Tr069WanVerification.ipSatisfied("", "", pppoe = true))
    }

    @Test
    fun `en IP estatica la IP observada debe coincidir exactamente`() {
        assertTrue(Tr069WanVerification.ipSatisfied("192.168.25.10", "192.168.25.10", pppoe = false))
        assertFalse(Tr069WanVerification.ipSatisfied("192.168.25.10", "192.168.25.11", pppoe = false))
    }

    @Test
    fun `la WAN solo se considera arriba con ConnectionStatus Connected`() {
        assertTrue(Tr069WanVerification.wanUp("Connected"))
        assertTrue(Tr069WanVerification.wanUp("connected"))
        assertFalse(Tr069WanVerification.wanUp("Connecting"))
        assertFalse(Tr069WanVerification.wanUp(null))
    }

    @Test
    fun `el SPV PPPoE del V2804 solo usa hojas escribibles del arbol GenieACS`() {
        val ppp =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1"
        val staticWan =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1"
        val values = v2804Profile().forClientPppoeWan().buildClientPppoeWanParameterValues(
            username = "gf2389",
            password = "lab-pppoe",
            vlanId = 100,
            connectionName = "GIGAFIBER-2389",
            replacedWanIpPath = staticWan,
        )
        val leaves = values.map { it.path.substringAfterLast('.') }.toSet()
        val unknown = leaves - v2804WritablePppoeLeaves
        assertTrue(
            unknown.isEmpty(),
            "hojas que el V2804 no expone como escribibles en WANPPP: $unknown (${values.map { it.path }})",
        )
        val byPath = values.associate { it.path to it.value }
        assertEquals("false", byPath["$staticWan.Enable"])
        assertEquals("AlwaysOn", byPath["$ppp.ConnectionTrigger"])
        assertEquals("gf2389", byPath["$ppp.Username"])
        assertEquals("100", byPath["$ppp.X_ZTE-COM_VLANID"])
        assertEquals("1", byPath["$ppp.X_ZTE-COM_VLANEnable"])
        assertEquals("INTERNET", byPath["$ppp.X_CT-COM_ServiceList"])
        assertFalse(values.any { it.path.contains("X_HW_") })
        assertFalse(values.any { it.path.endsWith(".ExternalIPAddress") })
    }

    private val v2804WritablePppoeLeaves = setOf(
        "Enable",
        "ConnectionType",
        "Name",
        "Alias",
        "Username",
        "Password",
        "NATEnabled",
        "ConnectionTrigger",
        "X_CT-COM_ServiceList",
        "X_ZTE-COM_ServiceList",
        "X_CT-COM_VLANIDMark",
        "X_ZTE-COM_VLANID",
        "X_ZTE-COM_VLANEnable",
        "VLANIDMark",
    )

    private fun v2804Profile() = Tr069ModelProfile(
        productClass = "V2804AX15T",
        wanIpConnectionPath =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
        wanGponLinkConfigPath =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_CT-COM_WANGponLinkConfig",
        wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
        wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
        vlanParameters = listOf(
            Tr069VlanParameterSpec(
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_CT-COM_VLANIDMark"
            ),
            Tr069VlanParameterSpec(
                path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANEnable",
                valueKind = Tr069VlanValueKind.ENABLE_ONE,
            ),
            Tr069VlanParameterSpec(
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANID"
            ),
            Tr069VlanParameterSpec(
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_CT-COM_WANGponLinkConfig.VLANIDMark"
            ),
        ),
        clientWanPppConnectionPath =
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1",
    )

    private fun request(username: String?, password: String?) = Tr069ProvisionRequest(
        onuSerial = "ZTEGDC47BFFD",
        onuTypeName = "F6600R",
        ip = null,
        ipSegment = null,
        wifiSsid24 = "gf24",
        wifiPassword24 = "12345678",
        wifiSsid5 = "gf5",
        wifiPassword5 = "12345678",
        wanVlanId = 100,
        pppoeUsername = username,
        pppoePassword = password,
    )
}
