package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice

interface IMikroTikService {
    
    fun removeIpFromDebtorsList(session: MikrotikSession, ip: String)
    
    fun addIpToDebtorsList(session: MikrotikSession, ip: String, comment: String)
    
    fun addIpToDebtorsListIfNotExists(session: MikrotikSession, ip: String, comment: String)
    
    fun removeFirewallRulesByComment(session: MikrotikSession, commentPattern: String)
    
    fun createFirewallDropRule(session: MikrotikSession)
    
    fun clearAddressList(session: MikrotikSession, listName: String): Int
    
    fun clearFirewallRules(session: MikrotikSession): Int
    
    fun findAndRemoveQueueByIp(session: MikrotikSession, ip: String)
    
    fun executeOnDevice(device: NetworkDevice, block: (MikrotikSession) -> Unit)
    
    fun checkIfAddressExistsInList(session: MikrotikSession, listName: String, address: String): Boolean
}
