package com.dscorp.wispadmin.wispadmin.response

data class EthernetPort(
    val admin_state: String,
    val allowed_vlans: String,
    val dhcp: String,
    val mode: String,
    val port: String,
    val vlan: String
){
    constructor() : this("", "", "", "", "", "")
}