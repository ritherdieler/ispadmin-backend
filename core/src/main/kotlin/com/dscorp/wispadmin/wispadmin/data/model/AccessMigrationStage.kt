package com.dscorp.wispadmin.wispadmin.data.model

enum class AccessMigrationStage {
    ELIGIBLE,
    OLT_READY,
    SECRET_READY,
    CPE_APPLIED,
    VERIFIED,
    QUEUE_CLEARED,
    QUARANTINE,
    DONE,
    FAILED_REVERTED,
    FAILED_STRANDED,
    ;

    fun isTerminal(): Boolean =
        this == DONE || this == FAILED_REVERTED || this == FAILED_STRANDED

    fun isActive(): Boolean = this != DONE && this != FAILED_REVERTED && this != FAILED_STRANDED

    fun isClientComplete(): Boolean = this == QUARANTINE || isTerminal()

    fun toClientMessage(): String = when (this) {
        ELIGIBLE -> "Elegible para migrar a PPPoE"
        OLT_READY -> "Service-port VLAN 100 verificado"
        SECRET_READY -> "Secret PPPoE creado"
        CPE_APPLIED -> "WAN PPPoE aplicada en el CPE"
        VERIFIED -> "Sesión PPPoE verificada"
        QUEUE_CLEARED -> "Cola por IP eliminada"
        QUARANTINE -> "En cuarentena"
        DONE -> "Migración completada"
        FAILED_REVERTED -> "Falló y se revirtió"
        FAILED_STRANDED -> "Falló y el CPE no responde"
    }
}
