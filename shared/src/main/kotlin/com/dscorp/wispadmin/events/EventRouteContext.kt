package com.dscorp.wispadmin.events

object EventRouteContext {
    private val namespace = ThreadLocal<String?>()

    fun setNamespace(value: String?) {
        namespace.set(value?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun namespace(): String? = namespace.get()

    fun clear() {
        namespace.remove()
    }
}
