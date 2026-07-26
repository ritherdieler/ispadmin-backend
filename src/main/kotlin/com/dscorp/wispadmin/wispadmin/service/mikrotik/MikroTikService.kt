package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.observability.tracing.ObsTracer
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MikroTikService(
    private val obsTracer: ObsTracer
) : IMikroTikService {
    
    private val logger = LoggerFactory.getLogger(MikroTikService::class.java)
    
    companion object {
        private const val DEBTORS_LIST = "deudores"
        private const val FIREWALL_DROP_RULE_COMMENT = "CORTADO POR DEUDA - LISTA DE DEUDORES"
    }
    
    override fun removeIpFromDebtorsList(session: MikrotikSession, ip: String) = obsTracer.span("mikrotik: removeIpFromDebtorsList") {
        val addressListQuery = "/ip/firewall/address-list/print where list=$DEBTORS_LIST and address=$ip"
        val addressListResult = session.execute(addressListQuery)
        addressListResult.forEach { addressEntry ->
            session.execute("/ip/firewall/address-list/remove numbers=${addressEntry[".id"]}")
        }
    }
    
    override fun addIpToDebtorsList(session: MikrotikSession, ip: String, comment: String) = obsTracer.span("mikrotik: addIpToDebtorsList") {
        session.execute(
            "/ip/firewall/address-list/add list=$DEBTORS_LIST address=$ip comment='$comment'"
        )
        Unit
    }
    
    override fun addIpToDebtorsListIfNotExists(session: MikrotikSession, ip: String, comment: String) = obsTracer.span("mikrotik: addIpToDebtorsListIfNotExists") {
        val existingAddress = session.execute(
            "/ip/firewall/address-list/print where list=$DEBTORS_LIST and address=$ip"
        )
        if (existingAddress.isEmpty()) {
            session.execute(
                "/ip/firewall/address-list/add list=$DEBTORS_LIST address=$ip comment='$comment'"
            )
        }
        Unit
    }
    
    override fun removeFirewallRulesByComment(session: MikrotikSession, commentPattern: String) = obsTracer.span("mikrotik: removeFirewallRulesByComment") {
        val existingRules = session.execute("/ip/firewall/filter/print where comment='$commentPattern'")
        existingRules.forEach { rule ->
            session.execute("/ip/firewall/filter/remove numbers=${rule[".id"]}")
        }
    }
    
    override fun createFirewallDropRule(session: MikrotikSession) = obsTracer.span("mikrotik: createFirewallDropRule") {
        removeFirewallRulesByComment(session, FIREWALL_DROP_RULE_COMMENT)
        session.execute(
            "/ip/firewall/filter/add chain=forward action=drop src-address-list=$DEBTORS_LIST comment='$FIREWALL_DROP_RULE_COMMENT'"
        )
        Unit
    }
    
    override fun clearAddressList(session: MikrotikSession, listName: String): Int = obsTracer.span("mikrotik: clearAddressList") {
        val existingAddressList = session.execute("/ip/firewall/address-list/print where list=$listName")
        val deletedCount = existingAddressList.size
        existingAddressList.forEach { addressEntry ->
            session.execute("/ip/firewall/address-list/remove numbers=${addressEntry[".id"]}")
        }
        deletedCount
    }
    
    override fun clearFirewallRules(session: MikrotikSession): Int = obsTracer.span("mikrotik: clearFirewallRules") {
        val existingRules = session.execute("/ip/firewall/filter/print")
        val matchingRules = existingRules.filter { rule ->
            rule["comment"]?.contains("CORTADO POR DEUDA") == true
        }
        val deletedCount = matchingRules.size
        matchingRules.forEach { rule ->
            session.execute("/ip/firewall/filter/remove numbers=${rule[".id"]}")
        }
        deletedCount
    }
    
    override fun findAndRemoveQueueByIp(session: MikrotikSession, ip: String) = obsTracer.span("mikrotik: findAndRemoveQueueByIp") {
        val query = "/queue/simple/print where target=$ip/32"
        val result = session.execute(query)
        result.lastOrNull()?.let { map ->
            session.execute("/queue/simple/remove numbers=${map[".id"]}")
        }
        Unit
    }
    
    override fun executeOnDevice(device: NetworkDevice, block: (MikrotikSession) -> Unit) = obsTracer.span("mikrotik: executeOnDevice") {
        device.executeCommand(block)
    }
    
    override fun checkIfAddressExistsInList(session: MikrotikSession, listName: String, address: String): Boolean = obsTracer.span("mikrotik: checkIfAddressExistsInList") {
        val result = session.execute(
            "/ip/firewall/address-list/print where list=$listName and address=$address"
        )
        result.isNotEmpty()
    }
}
