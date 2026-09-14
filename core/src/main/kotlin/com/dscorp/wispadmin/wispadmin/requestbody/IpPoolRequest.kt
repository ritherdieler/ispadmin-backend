package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice

data class IpPoolRequest(
    var ipSegment: String,
    val hostDeviceId:Int
) {

}