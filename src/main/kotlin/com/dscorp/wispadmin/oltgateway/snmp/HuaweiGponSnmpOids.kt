package com.dscorp.wispadmin.oltgateway.snmp

/**
 * Column OIDs under hwXponDeviceMIB verified on Gigafiber MA5608T.
 * Index: {ifIndex}.{ontId}
 */
object HuaweiGponSnmpOids {
    const val ENTERPRISE_SYS_OBJECT = "1.3.6.1.4.1.2011.2.248"

    const val ONT_SN = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1.3"
    const val ONT_AUTH_METHOD = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1.2"
    const val ONT_LINE_PROF_NAME = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1.7"
    const val ONT_SERVICE_PROF_NAME = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1.8"
    const val ONT_DESCRIPTION = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1.9"

    const val ONT_RUN_STATUS = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1.15"
    const val ONT_MATCH_STATUS = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1.18"
    const val ONT_RANGING = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1.20"
    const val ONT_MAC_COUNT = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1.21"
    const val ONT_LAST_DOWN_CAUSE = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1.24"

    /** Mapping verified live on Gigafiber MA5608T: .4 Rx, .5 Tx, .6 OLT Rx (do not remap from MIB name tables). */
    const val ONT_OPTICAL_TEMPERATURE = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.1"
    const val ONT_OPTICAL_BIAS = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.2"
    const val ONT_RX_POWER = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.4"
    const val ONT_TX_POWER = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.5"
    const val OLT_RX_POWER = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.6"

    const val AUTOFIND_SN = "1.3.6.1.4.1.2011.6.128.1.1.2.52.1.2"
}
