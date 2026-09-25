package com.dscorp.wispadmin.oltgateway.config

object GatewayCallContext {
    private val env = ThreadLocal<String?>()
    private val labInventoryOnly = ThreadLocal<Boolean>()

    fun setEnv(value: String?) {
        env.set(value?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun env(): String? = env.get()

    fun setLabInventoryOnly(value: Boolean) {
        labInventoryOnly.set(value)
    }

    fun labInventoryOnly(): Boolean = labInventoryOnly.get() == true

    fun clear() {
        env.remove()
        labInventoryOnly.remove()
    }
}
