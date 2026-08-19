package com.dscorp.wispadmin.wispadmin.genieacs

class CpeDeviceOfflineException(
    sn: String,
    lastInform: String?,
) : RuntimeException(
    if (lastInform.isNullOrBlank()) {
        "El equipo $sn no ha informado al ACS"
    } else {
        "El equipo $sn está offline en ACS (último Inform: $lastInform)"
    }
)
