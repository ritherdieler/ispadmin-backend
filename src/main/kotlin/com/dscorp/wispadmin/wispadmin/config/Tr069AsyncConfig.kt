package com.dscorp.wispadmin.wispadmin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class Tr069AsyncConfig {

    @Bean(name = ["tr069TaskExecutor"])
    fun tr069TaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 64
        executor.setThreadNamePrefix("tr069-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(120)
        executor.initialize()
        return executor
    }
}
