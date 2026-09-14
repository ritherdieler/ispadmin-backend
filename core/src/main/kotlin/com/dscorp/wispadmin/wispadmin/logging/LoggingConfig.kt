package com.dscorp.wispadmin.wispadmin.logging

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/**
 * Configuración para el sistema de logging y manejo de excepciones
 */
@Configuration
@EnableAspectJAutoProxy
class LoggingConfig {
    // La anotación @EnableAspectJAutoProxy permite el funcionamiento de los aspectos AOP
} 