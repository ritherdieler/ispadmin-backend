package com.dscorp.wispadmin.wispadmin.data.model

enum class InstallationType {
    FIBER,
    WIRELESS,
    ONLY_TV_FIBER;

}


fun InstallationType.isInternetUser() = (this == InstallationType.FIBER || this == InstallationType.WIRELESS)


