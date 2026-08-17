package com.dscorp.wispadmin.wispadmin.genieacs

class GenieAcsDeviceNotFoundException(sn: String) :
    RuntimeException("No se encontró el CPE en GenieACS para SN $sn")
