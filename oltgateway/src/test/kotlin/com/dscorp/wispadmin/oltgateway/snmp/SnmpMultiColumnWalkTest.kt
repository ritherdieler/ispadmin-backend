package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.smi.Integer32
import org.snmp4j.smi.Null
import org.snmp4j.smi.OID
import org.snmp4j.smi.VariableBinding
import java.io.IOException

class SnmpMultiColumnWalkTest {

    private val rxRoot = HuaweiGponSnmpOids.ONT_RX_POWER
    private val txRoot = HuaweiGponSnmpOids.ONT_TX_POWER
    private val oltRxRoot = HuaweiGponSnmpOids.OLT_RX_POWER

    @Test
    fun `reparte bindings intercalados entre tres columnas`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.1", -2501),
                    vb("$txRoot.16909060.1", 201),
                    vb("$oltRxRoot.16909060.1", -2701),
                    vb("$rxRoot.16909060.2", -2502),
                    vb("$txRoot.16909060.2", 202),
                    vb("$oltRxRoot.16909060.2", -2702),
                )
            )
        )

        val columns = walk(sender, listOf(rxRoot, txRoot, oltRxRoot))

        assertEquals(
            listOf("$rxRoot.16909060.1", "$rxRoot.16909060.2"),
            columns[0].map { it.oid.toString() }
        )
        assertEquals(
            listOf("$txRoot.16909060.1", "$txRoot.16909060.2"),
            columns[1].map { it.oid.toString() }
        )
        assertEquals(
            listOf("$oltRxRoot.16909060.1", "$oltRxRoot.16909060.2"),
            columns[2].map { it.oid.toString() }
        )
        assertEquals(listOf(rxRoot, txRoot, oltRxRoot), sender.requests.first())
    }

    @Test
    fun `avanza cada cursor al ultimo OID de su propia columna`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.1", -2501),
                    vb("$txRoot.16909060.1", 201),
                    vb("$rxRoot.16909060.2", -2502),
                    vb("$txRoot.16909060.2", 202),
                )
            )
        )

        walk(sender, listOf(rxRoot, txRoot))

        assertEquals(
            listOf("$rxRoot.16909060.2", "$txRoot.16909060.2"),
            sender.requests[1]
        )
    }

    @Test
    fun `compara cursores como OID numerico y no como texto`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.8", -2508),
                    vb("$txRoot.16909060.8", 208),
                ),
                listOf(
                    vb("$rxRoot.16909060.23", -2523),
                    vb("$txRoot.16909060.23", 223),
                ),
            )
        )

        val columns = walk(sender, listOf(rxRoot, txRoot))

        assertEquals(
            listOf("$rxRoot.16909060.8", "$rxRoot.16909060.23"),
            columns[0].map { it.oid.toString() }
        )
        assertEquals(
            listOf("$txRoot.16909060.8", "$txRoot.16909060.23"),
            columns[1].map { it.oid.toString() }
        )
    }

    @Test
    fun `endOfMibView desactiva solo su columna`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.1", -2501),
                    endOfMib("$txRoot.16909060.1"),
                    vb("$rxRoot.16909060.2", -2502),
                    endOfMib("$txRoot.16909060.1"),
                ),
                listOf(
                    vb("$rxRoot.16909060.3", -2503),
                ),
            )
        )

        val columns = walk(sender, listOf(rxRoot, txRoot))

        assertEquals(3, columns[0].size)
        assertTrue(columns[1].isEmpty(), "tx column should be empty")
        assertEquals(listOf("$rxRoot.16909060.2"), sender.requests[1])
    }

    @Test
    fun `columna agotada que desborda al subarbol vecino no contamina a la vecina`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.1", -2501),
                    vb("$txRoot.16909060.1", 201),
                ),
                listOf(
                    vb("$txRoot.16909060.1", 201),
                    vb("$txRoot.16909060.2", 202),
                ),
            )
        )

        val columns = walk(sender, listOf(rxRoot, txRoot))

        assertEquals(listOf("$rxRoot.16909060.1"), columns[0].map { it.oid.toString() })
        assertEquals(
            listOf("$txRoot.16909060.1", "$txRoot.16909060.2"),
            columns[1].map { it.oid.toString() }
        )
        assertEquals(listOf("$txRoot.16909060.2"), sender.requests[2])
    }

    @Test
    fun `columnas terminan en paginas distintas`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.1", -2501),
                    vb("$txRoot.16909060.1", 201),
                ),
                listOf(
                    vb("$rxRoot.16909060.2", -2502),
                    endOfMib("$txRoot.16909060.1"),
                ),
                listOf(
                    vb("$rxRoot.16909060.3", -2503),
                ),
            )
        )

        val columns = walk(sender, listOf(rxRoot, txRoot))

        assertEquals(3, columns[0].size)
        assertEquals(1, columns[1].size)
        assertEquals(2, sender.requests[1].size)
        assertEquals(1, sender.requests[2].size)
    }

    @Test
    fun `respuesta parcial sigue desde el ultimo OID recibido por columna`() {
        val sender = ScriptedSender(
            listOf(
                listOf(
                    vb("$rxRoot.16909060.1", -2501),
                    vb("$txRoot.16909060.1", 201),
                    vb("$rxRoot.16909060.2", -2502),
                ),
                listOf(
                    vb("$rxRoot.16909060.3", -2503),
                    vb("$txRoot.16909060.2", 202),
                ),
            )
        )

        val columns = walk(sender, listOf(rxRoot, txRoot))

        assertEquals(
            listOf("$rxRoot.16909060.2", "$txRoot.16909060.1"),
            sender.requests[1]
        )
        assertEquals(3, columns[0].size)
        assertEquals(2, columns[1].size)
    }

    @Test
    fun `respuesta no advancing lanza IOException`() {
        val sender = ScriptedSender(
            listOf(
                listOf(vb("$rxRoot.16909060.1", -2501)),
                listOf(vb("$rxRoot.16909060.1", -2501)),
            )
        )

        val ex = assertThrows(IOException::class.java) { walk(sender, listOf(rxRoot)) }
        assertTrue(ex.message!!.contains("non-advancing"), ex.message)
    }

    @Test
    fun `respuesta vacia lanza IOException`() {
        val walk = SnmpMultiColumnWalk(sendPage = { emptyList() })

        val ex = assertThrows(IOException::class.java) {
            walk.walk(listOf(OID(rxRoot)), "optical")
        }
        assertTrue(ex.message!!.contains("empty response"), ex.message)
    }

    @Test
    fun `aplica pacing solo entre paginas`() {
        val sender = ScriptedSender(
            listOf(
                listOf(vb("$rxRoot.16909060.1", -2501)),
                listOf(vb("$rxRoot.16909060.2", -2502)),
            )
        )
        var paced = 0

        SnmpMultiColumnWalk(
            sendPage = { cursors -> sender.send(cursors) },
            betweenPages = { paced++ },
        ).walk(listOf(OID(rxRoot)), "optical")

        assertEquals(3, sender.requests.size)
        assertEquals(2, paced)
    }

    private fun walk(sender: ScriptedSender, roots: List<String>): List<List<VariableBinding>> {
        return SnmpMultiColumnWalk(sendPage = { cursors -> sender.send(cursors) })
            .walk(roots.map { OID(it) }, "optical")
    }

    private fun vb(oid: String, value: Int) = VariableBinding(OID(oid), Integer32(value))

    private fun endOfMib(oid: String) = VariableBinding(OID(oid), Null(Null.endOfMibView.syntax))

    private class ScriptedSender(private val pages: List<List<VariableBinding>>) {
        val requests = mutableListOf<List<String>>()
        private var index = 0

        fun send(cursors: List<OID>): List<VariableBinding> {
            requests += cursors.map { it.toString() }
            val page = pages.getOrNull(index)
            index++
            return page ?: cursors.map { VariableBinding(OID("1.3.6.1.4.1.2011.6.128.1.1.2.99.1.1.1"), Integer32(0)) }
        }
    }
}
