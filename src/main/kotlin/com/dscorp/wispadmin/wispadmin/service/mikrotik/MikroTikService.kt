package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import me.legrange.mikrotik.ApiConnection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MikroTikService : IMikroTikService {
    
    private val logger = LoggerFactory.getLogger(MikroTikService::class.java)
    
    companion object {
        private const val DEBTORS_LIST = "deudores"
        private const val FIREWALL_DROP_RULE_COMMENT = "CORTADO POR DEUDA - LISTA DE DEUDORES"
    }
    
    override fun removeIpFromDebtorsList(connection: ApiConnection, ip: String) {
        val addressListQuery = "/ip/firewall/address-list/print where list=$DEBTORS_LIST and address=$ip"
        val addressListResult = connection.execute(addressListQuery)
        addressListResult.forEach { addressEntry ->
            connection.execute("/ip/firewall/address-list/remove numbers=${addressEntry[".id"]}")
        }
    }
    
    override fun addIpToDebtorsList(connection: ApiConnection, ip: String, comment: String) {
        connection.execute(
            "/ip/firewall/address-list/add list=$DEBTORS_LIST address=$ip comment='$comment'"
        )
    }
    
    override fun addIpToDebtorsListIfNotExists(connection: ApiConnection, ip: String, comment: String) {
        val existingAddress = connection.execute(
            "/ip/firewall/address-list/print where list=$DEBTORS_LIST and address=$ip"
        )
        if (existingAddress.isEmpty()) {
            connection.execute(
                "/ip/firewall/address-list/add list=$DEBTORS_LIST address=$ip comment='$comment'"
            )
        }
    }
    
    override fun removeFirewallRulesByComment(connection: ApiConnection, commentPattern: String) {
        val existingRules = connection.execute("/ip/firewall/filter/print where comment='$commentPattern'")
        existingRules.forEach { rule ->
            connection.execute("/ip/firewall/filter/remove numbers=${rule[".id"]}")
        }
    }
    
    override fun createFirewallDropRule(connection: ApiConnection) {
        removeFirewallRulesByComment(connection, FIREWALL_DROP_RULE_COMMENT)
        connection.execute(
            "/ip/firewall/filter/add chain=forward action=drop src-address-list=$DEBTORS_LIST comment='$FIREWALL_DROP_RULE_COMMENT'"
        )
    }
    
    override fun clearAddressList(connection: ApiConnection, listName: String): Int {
        val existingAddressList = connection.execute("/ip/firewall/address-list/print where list=$listName")
        val deletedCount = existingAddressList.size
        existingAddressList.forEach { addressEntry ->
            connection.execute("/ip/firewall/address-list/remove numbers=${addressEntry[".id"]}")
        }
        return deletedCount
    }
    
    override fun clearFirewallRules(connection: ApiConnection): Int {
        val existingRules = connection.execute("/ip/firewall/filter/print")
        val matchingRules = existingRules.filter { rule ->
            rule["comment"]?.contains("CORTADO POR DEUDA") == true
        }
        val deletedCount = matchingRules.size
        matchingRules.forEach { rule ->
            connection.execute("/ip/firewall/filter/remove numbers=${rule[".id"]}")
        }
        return deletedCount
    }
    
    override fun findAndRemoveQueueByIp(connection: ApiConnection, ip: String) {
        val query = "/queue/simple/print where target=$ip/32"
        val result = connection.execute(query)
        result.lastOrNull()?.let { map ->
            connection.execute("/queue/simple/remove numbers=${map[".id"]}")
        }
    }
    
    override fun executeOnDevice(device: NetworkDevice, block: (ApiConnection) -> Unit) {
        device.executeCommand(block)
    }
    
    override fun checkIfAddressExistsInList(connection: ApiConnection, listName: String, address: String): Boolean {
        val result = connection.execute(
            "/ip/firewall/address-list/print where list=$listName and address=$address"
        )
        return result.isNotEmpty()
    }
}



