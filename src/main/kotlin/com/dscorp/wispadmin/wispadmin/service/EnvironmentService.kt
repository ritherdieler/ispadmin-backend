package com.dscorp.wispadmin.wispadmin.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EnvironmentService {
    
    @Value("\${spring.profiles.active:dev}")
    private lateinit var activeProfile: String
    
    fun isDevelopment(): Boolean {
        return activeProfile.split(",")
            .map { it.trim() }
            .any { it.equals("dev", ignoreCase = true) }
    }
    
    fun getActiveProfile(): String {
        return activeProfile
    }
}
