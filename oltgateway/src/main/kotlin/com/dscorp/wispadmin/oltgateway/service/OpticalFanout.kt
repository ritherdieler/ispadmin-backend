package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.OnuOpticalBatchPayload

internal val labOpticalNamespaces = setOf("stg", "lpstg")

internal fun opticalPayloadsByNamespace(
    payload: OnuOpticalBatchPayload,
    namespaces: List<String>,
    isLab: (String) -> Boolean,
): List<Pair<String, OnuOpticalBatchPayload>> {
    return namespaces.mapNotNull { namespace ->
        if (namespace !in labOpticalNamespaces) return@mapNotNull namespace to payload
        val labOnly = payload.copy(onus = payload.onus.filter { isLab(it.sn) })
        if (labOnly.onus.isEmpty()) null else namespace to labOnly
    }
}
