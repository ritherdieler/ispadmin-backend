package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OnuWriteProvider
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProviderProperties
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltAuthorizeCommand
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltMoveCommand
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltWriteClient
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltWriteResult

class OnuWriteRouter(
    private val properties: OnuWriteProviderProperties,
    private val commandService: OltGatewayCommandService,
    private val smartOlt: SmartOltWriteClient,
) {

    fun authorize(ssh: AuthorizeCliRequest, cloud: SmartOltAuthorizeCommand): SmartOltWriteResult {
        return when (properties.authorize) {
            OnuWriteProvider.SMARTOLT -> smartOlt.authorize(cloud)
            OnuWriteProvider.GATEWAY -> {
                commandService.authorize(ssh)
                SmartOltWriteResult(true, uniqueExternalId = null)
            }
        }
    }

    fun delete(ssh: DeleteCliRequest, externalId: String): SmartOltWriteResult {
        return when (properties.delete) {
            OnuWriteProvider.SMARTOLT -> smartOlt.delete(externalId)
            OnuWriteProvider.GATEWAY -> {
                commandService.delete(ssh)
                SmartOltWriteResult(true, uniqueExternalId = externalId)
            }
        }
    }

    fun reboot(ssh: RebootCliRequest, externalId: String): SmartOltWriteResult {
        return when (properties.reboot) {
            OnuWriteProvider.SMARTOLT -> smartOlt.reboot(externalId)
            OnuWriteProvider.GATEWAY -> {
                commandService.reboot(ssh)
                SmartOltWriteResult(true, uniqueExternalId = externalId)
            }
        }
    }

    fun move(ssh: MoveCliRequest, sn: String, cloud: SmartOltMoveCommand): SmartOltWriteResult {
        return when (properties.move) {
            OnuWriteProvider.SMARTOLT -> smartOlt.move(sn, cloud)
            OnuWriteProvider.GATEWAY -> {
                commandService.move(ssh)
                SmartOltWriteResult(true, uniqueExternalId = null)
            }
        }
    }
}
