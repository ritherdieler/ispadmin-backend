package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.tracing.Tracer
import org.springframework.stereotype.Service

@Service
class MikroTikService(
    private val obsTracer: Tracer
) : IMikroTikService {

    companion object {
        private val DEBTORS_LIST = CutLists.DEBTORS
        private const val PATH_ADDRESS_LIST = "/ip/firewall/address-list"
        private const val PATH_FIREWALL_FILTER = "/ip/firewall/filter"
        private const val PATH_QUEUE_SIMPLE = "/queue/simple"
    }

    override fun removeIpFromDebtorsList(session: MikrotikSession, ip: String) =
        removeIpFromCutList(session, DEBTORS_LIST, ip)

    override fun addIpToDebtorsList(session: MikrotikSession, ip: String, comment: String) =
        addIpToCutList(session, DEBTORS_LIST, ip, comment)

    override fun addIpToDebtorsListIfNotExists(session: MikrotikSession, ip: String, comment: String) =
        obsTracer.span("mikrotik: addIpToDebtorsListIfNotExists") {
            val existingAddress =
                session.print(PATH_ADDRESS_LIST, mapOf("list" to DEBTORS_LIST.name, "address" to ip))
            if (existingAddress.isEmpty()) {
                addIpToDebtorsList(session, ip, comment)
            }
            Unit
        }

    override fun addIpToCutList(session: MikrotikSession, list: CutList, ip: String, comment: String) =
        obsTracer.span("mikrotik: addIpToCutList") {
            session.add(
                PATH_ADDRESS_LIST,
                mapOf(
                    "list" to list.name,
                    "address" to ip,
                    "comment" to comment
                )
            )
            Unit
        }

    override fun removeIpFromCutList(session: MikrotikSession, list: CutList, ip: String) =
        obsTracer.span("mikrotik: removeIpFromCutList") {
            session.print(PATH_ADDRESS_LIST, mapOf("list" to list.name, "address" to ip))
                .forEach { addressEntry ->
                    addressEntry[".id"]?.let { id -> session.remove(PATH_ADDRESS_LIST, id) }
                }
        }

    override fun removeIpFromAllCutLists(session: MikrotikSession, ip: String) =
        obsTracer.span("mikrotik: removeIpFromAllCutLists") {
            CutLists.ALL.forEach { list -> removeIpFromCutList(session, list, ip) }
        }

    override fun createCutDropRule(session: MikrotikSession, list: CutList) =
        obsTracer.span("mikrotik: createCutDropRule") {
            val placeBefore = DebtorCutRulePlacement.placeBeforeId(session.print(PATH_FIREWALL_FILTER))
            removeFirewallRulesByComment(session, list.dropComment)
            val args = mutableMapOf(
                "chain" to "forward",
                "action" to "drop",
                "src-address-list" to list.name,
                "comment" to list.dropComment,
            )
            if (placeBefore != null) {
                args["place-before"] = placeBefore
            }
            session.add(PATH_FIREWALL_FILTER, args)
            Unit
        }

    override fun removeFirewallRulesByComment(session: MikrotikSession, commentPattern: String) =
        obsTracer.span("mikrotik: removeFirewallRulesByComment") {
            session.print(PATH_FIREWALL_FILTER, mapOf("comment" to commentPattern))
                .forEach { rule ->
                    rule[".id"]?.let { id -> session.remove(PATH_FIREWALL_FILTER, id) }
                }
        }

    override fun createFirewallDropRule(session: MikrotikSession) =
        createCutDropRule(session, DEBTORS_LIST)

    override fun clearAddressList(session: MikrotikSession, listName: String): Int = obsTracer.span("mikrotik: clearAddressList") {
        val existingAddressList = session.print(PATH_ADDRESS_LIST, mapOf("list" to listName))
        existingAddressList.forEach { addressEntry ->
            addressEntry[".id"]?.let { id -> session.remove(PATH_ADDRESS_LIST, id) }
        }
        existingAddressList.size
    }

    override fun findAndRemoveQueueByIp(session: MikrotikSession, ip: String) = obsTracer.span("mikrotik: findAndRemoveQueueByIp") {
        val result = session.print(PATH_QUEUE_SIMPLE, mapOf("target" to "$ip/32"))
        result.lastOrNull()?.get(".id")?.let { id ->
            session.remove(PATH_QUEUE_SIMPLE, id)
        }
        Unit
    }

    override fun executeOnDevice(device: NetworkDevice, block: (MikrotikSession) -> Unit) = obsTracer.span("mikrotik: executeOnDevice") {
        device.executeCommand(block)
    }

    override fun checkIfAddressExistsInList(session: MikrotikSession, listName: String, address: String): Boolean =
        obsTracer.span("mikrotik: checkIfAddressExistsInList") {
            session.print(PATH_ADDRESS_LIST, mapOf("list" to listName, "address" to address)).isNotEmpty()
        }
}
