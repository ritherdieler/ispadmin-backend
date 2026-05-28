package com.dscorp.wispadmin.wispadmin.data.model

/**
 * Enum que define los módulos disponibles en el sistema
 */
enum class Modules(val displayName: String) {
    USER("Usuario"),
    CUSTOMER("Cliente"),
    SUBSCRIPTION("Suscripción"),
    PLAN("Plan"),
    ADMIN("Administración"),
    DASHBOARD("Dashboard"),
    BILLING("Facturación"),
    PAYMENT("Pagos"),
    OUTLAY("Gastos"),
    IZIPAY("Izipay"),
    MICROTIC("Microtic"),
    LOG_VIEWER("Visor de Logs"),
    ASSISTANCE_TICKET("Tickets de Asistencia"),
    CUT_SERVICE("Corte de Servicio"),
    ONU("ONU"),
    UPDATING_SUBSCRIPTION_CUT_OFF("Actualización de Corte de Suscripción"),
    INSTALLATION_ORDER("Orden de Instalación"),
    GENERAL("General");

    override fun toString(): String = name
} 