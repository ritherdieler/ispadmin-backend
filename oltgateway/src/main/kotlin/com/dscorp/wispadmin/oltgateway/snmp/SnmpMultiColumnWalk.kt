package com.dscorp.wispadmin.oltgateway.snmp

import org.snmp4j.smi.OID
import org.snmp4j.smi.VariableBinding
import java.io.IOException

fun interface SnmpGetBulkPageSender {
    fun send(type: SnmpJobType, cursors: List<OID>, maxRepetitions: Int): List<VariableBinding>
}

/**
 * Walks several table columns with one multi-varbind GETBULK per page: N cursors in, N
 * interleaved repetition rounds out. RFC 3416 keeps the request order and pads exhausted
 * varbinds with endOfMibView, so a binding belongs to the column at `index % cursors.size`;
 * the root prefix is a guard, not the router — an exhausted column overflows into its
 * neighbour's subtree and prefix routing alone would corrupt that neighbour's cursor.
 */
class SnmpMultiColumnWalk(
    private val sendPage: (List<OID>) -> List<VariableBinding>,
    private val betweenPages: () -> Unit = {},
) {

    fun walk(roots: List<OID>, label: String): List<List<VariableBinding>> {
        val result = roots.map { mutableListOf<VariableBinding>() }
        val cursors = roots.toMutableList()
        val active = roots.indices.toMutableSet()
        while (active.isNotEmpty()) {
            val requested = active.sorted()
            val bindings = sendPage(requested.map { cursors[it] })
            if (bindings.isEmpty()) {
                throw IOException("SNMP walk error on $label: empty response")
            }
            var recorded = 0
            bindings.forEachIndexed { index, binding ->
                val column = requested[index % requested.size]
                if (column !in active) return@forEachIndexed
                val oid = binding.oid
                if (oid == null || binding.variable.isException || !oid.startsWith(roots[column])) {
                    active.remove(column)
                    return@forEachIndexed
                }
                if (oid.compareTo(cursors[column]) <= 0) {
                    throw IOException("SNMP walk error on $label: non-advancing response")
                }
                cursors[column] = oid
                result[column].add(binding)
                recorded++
            }
            if (recorded == 0 && active.isNotEmpty()) {
                throw IOException("SNMP walk error on $label: no progress")
            }
            if (active.isNotEmpty()) {
                betweenPages()
            }
        }
        return result
    }
}
