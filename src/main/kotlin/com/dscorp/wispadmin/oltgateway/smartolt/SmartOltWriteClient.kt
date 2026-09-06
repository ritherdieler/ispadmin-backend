package com.dscorp.wispadmin.oltgateway.smartolt

data class SmartOltAuthorizeCommand(
    val oltId: String,
    val ponType: String,
    val board: String,
    val port: String,
    val sn: String,
    val vlan: String,
    val onuType: String,
    val zone: String,
    val name: String,
    val onuMode: String,
    val customProfile: String,
)

data class SmartOltMoveCommand(
    val oltId: String,
    val board: String,
    val port: String,
)

data class SmartOltWriteResult(
    val status: Boolean,
    val uniqueExternalId: String?,
)

interface SmartOltWriteClient {
    fun authorize(command: SmartOltAuthorizeCommand): SmartOltWriteResult
    fun delete(externalId: String): SmartOltWriteResult
    fun reboot(externalId: String): SmartOltWriteResult
    fun move(sn: String, command: SmartOltMoveCommand): SmartOltWriteResult
}
