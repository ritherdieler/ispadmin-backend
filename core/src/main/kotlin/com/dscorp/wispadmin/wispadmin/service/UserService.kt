package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import org.springframework.stereotype.Service
import javax.persistence.EntityNotFoundException

@Service
class UserService(private val userRepository: UserRepository) {

    fun updateDeviceToken(id: Int, deviceToken: String): User {
        val user = userRepository.findById(id).orElseThrow { 
            EntityNotFoundException("User with id $id not found") 
        }
        user.deviceToken = deviceToken
        return userRepository.save(user)
    }
}
