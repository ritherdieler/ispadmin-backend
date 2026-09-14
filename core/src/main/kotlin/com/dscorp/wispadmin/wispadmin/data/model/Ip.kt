//package com.dscorp.wispadmin.wispadmin.data.model
//
//import com.fasterxml.jackson.annotation.JsonIgnore
//import javax.persistence.*
//
//@Entity
//data class Ip(
//    @Id
//    @GeneratedValue
//    val id: Int? = null,
//
//    val ip: String,
//    @JsonIgnore
//    @ManyToOne @JoinColumn(name = "ip_pool_id")
//    var ipPool: IpPool
//)