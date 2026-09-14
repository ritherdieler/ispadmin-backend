package com.dscorp.wispadmin.wispadmin.response

data class ServicePort(
    val cvlan: String,
    val download_speed: String,
    val service_port: String,
    val svlan: String,
    val tag_transform_mode: String,
    val upload_speed: String,
    val vlan: String
){
    constructor() : this("", "", "", "", "", "", "")
}