package com.dscorp.wispadmin.oltgateway.config

object GatewayCallContext {
    private val env = ThreadLocal<String?>()

    fun setEnv(value: String?) {
        env.set(value?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun env(): String? = env.get()

    fun clear() {
        env.remove()
    }
}
