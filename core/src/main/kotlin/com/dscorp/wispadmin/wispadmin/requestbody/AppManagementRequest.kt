package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.controller.ManagementAction

data class AppManagementRequest(
    val action: ManagementAction = ManagementAction.FORCE_LOGOUT
)