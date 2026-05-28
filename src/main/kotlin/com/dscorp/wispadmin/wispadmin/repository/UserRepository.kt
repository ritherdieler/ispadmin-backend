package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Int> {

    @Query("select u from User u where u.username=:username and u.password=:password")
    fun logIn(username:String, password:String):User?

    fun findByUsername(username: String): User?

    fun findByUsernameIgnoreCase(username: String): User?


//    get technicians by type
    @Query("select u from User u where u.type=:type and u.verified=true")
    fun getTechniciansByType(type: User.UserType):List<User>
}
