package com.dscorp.wispadmin.observability.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class ObservabilityAsyncConfig {

    @Bean(name = ["obsTaskExecutor"])
    fun obsTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 6
        executor.queueCapacity = 500
        executor.setThreadNamePrefix("obs-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }
}
