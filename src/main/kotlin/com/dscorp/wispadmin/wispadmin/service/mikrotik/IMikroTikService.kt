package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import me.legrange.mikrotik.ApiConnection

interface IMikroTikService {
    
    fun removeIpFromDebtorsList(connection: ApiConnection, ip: String)
    
    fun addIpToDebtorsList(connection: ApiConnection, ip: String, comment: String)
    
    fun addIpToDebtorsListIfNotExists(connection: ApiConnection, ip: String, comment: String)
    
    fun removeFirewallRulesByComment(connection: ApiConnection, commentPattern: String)
    
    fun createFirewallDropRule(connection: ApiConnection)
    
    fun clearAddressList(connection: ApiConnection, listName: String): Int
    
    fun clearFirewallRules(connection: ApiConnection): Int
    
    fun findAndRemoveQueueByIp(connection: ApiConnection, ip: String)
    
    fun executeOnDevice(device: NetworkDevice, block: (ApiConnection) -> Unit)
    
    fun checkIfAddressExistsInList(connection: ApiConnection, listName: String, address: String): Boolean
}



