package com.dscorp.wispadmin.wispadmin.data.model

enum class AccessMode {
    STATIC_IP,
    PPPOE_FIXED,
    PPPOE_DYNAMIC;
}

fun AccessMode.usesPppoe() = this == AccessMode.PPPOE_FIXED || this == AccessMode.PPPOE_DYNAMIC

fun AccessMode.usesStaticIpAddress() = this == AccessMode.STATIC_IP || this == AccessMode.PPPOE_FIXED

fun AccessMode.usesSimpleQueue() = usesStaticIpAddress()

fun AccessMode.usesAddressListCut() = usesStaticIpAddress()
