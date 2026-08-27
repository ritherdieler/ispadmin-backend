package com.dscorp.wispadmin.oltgateway.snmp

/**
 * Column OIDs under hwXponDeviceMIB verified on Gigafiber MA5608T.
 * Index: {ifIndex}.{ontId}
 */
object HuaweiGponSnmpOids {
    const val ENTERPRISE_SYS_OBJECT = "1.3.6.1.4.1.2011.2.248"

    const val ONT_SN = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1.3"
    const val ONT_RUN_STATUS = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1.15"
    const val ONT_RX_POWER = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.4"
    const val ONT_TX_POWER = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.5"
    const val OLT_RX_POWER = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1.6"
    const val AUTOFIND_SN = "1.3.6.1.4.1.2011.6.128.1.1.2.52.1.2"
}
