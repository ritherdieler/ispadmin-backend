package com.dscorp.wispadmin.wispadmin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {
    @Bean
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()
        
        // Permitir solicitudes desde orígenes de desarrollo
        // Usar patrones para permitir cualquier puerto en localhost
        config.addAllowedOriginPattern("http://localhost:*") // Cualquier puerto en localhost
        config.addAllowedOriginPattern("http://127.0.0.1:*") // Cualquier puerto en 127.0.0.1
        config.addAllowedOriginPattern("http://192.168.*.*:*") // Cualquier IP local en red 192.168.x.x
        config.addAllowedOriginPattern("http://10.*.*.*:*") // Cualquier IP local en red 10.x.x.x
        config.addAllowedOriginPattern("http://172.16.*.*:*") // Cualquier IP local en red 172.16.x.x-172.31.x.x
        
        // Orígenes específicos para desarrollo (mantener compatibilidad)
        config.addAllowedOrigin("http://localhost:3000") // Puerto común para desarrollos React
        config.addAllowedOrigin("http://localhost:5173") // Puerto por defecto de Vite
        
        // Permitir todos los métodos HTTP
        config.addAllowedMethod("*")
        
        // Permitir todas las cabeceras
        config.addAllowedHeader("*")
        
        // Permitir credenciales
        config.allowCredentials = true
        
        // Configurar el tiempo máximo de caché para las respuestas preflight
        config.maxAge = 3600L
        
        // Aplicar esta configuración a todas las rutas
        source.registerCorsConfiguration("/**", config)
        
        return CorsFilter(source)
    }
}