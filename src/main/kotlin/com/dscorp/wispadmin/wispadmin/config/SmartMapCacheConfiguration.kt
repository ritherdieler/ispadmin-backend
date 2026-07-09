package com.dscorp.wispadmin.wispadmin.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class SmartMapCacheConfiguration {

    // Cache de corta duracion para aliviar el resumen del Mapa Inteligente (query pesada).
    @Bean
    fun smartMapCacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(SMART_MAP_SUMMARY_CACHE, SMART_MAP_SUGGESTIONS_CACHE)
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(200)
        )
        return cacheManager
    }

    companion object {
        const val SMART_MAP_SUMMARY_CACHE = "smartMapSummary"
        const val SMART_MAP_SUGGESTIONS_CACHE = "smartMapSuggestions"
    }
}
