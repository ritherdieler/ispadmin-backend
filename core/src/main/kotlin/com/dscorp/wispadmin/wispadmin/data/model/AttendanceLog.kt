//package com.dscorp.wispadmin.wispadmin.data.model
//
//import javax.persistence.Entity
//import javax.persistence.GeneratedValue
//import javax.persistence.GenerationType
//import javax.persistence.Id
//import javax.persistence.ManyToOne
//import java.util.Date
//import javax.persistence.JoinColumn
//
//@Entity
//data class AttendanceLog(
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    val id: Int,
//
//    val action: String,
//    val timestamp: Date = Date(),
//    val deviceInfo: String? = null,
//
//    @ManyToOne
//    @JoinColumn(name = "user_id", nullable = false)
//    val user: User
//)
