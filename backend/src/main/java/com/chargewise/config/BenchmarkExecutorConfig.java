package com.chargewise.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Benchmark runs execute on one dedicated thread, so two runs never compete for CPU and distort each other's timings;
 * up to {@value #QUEUE_CAPACITY} further runs wait their turn.
 */
@Configuration(proxyBeanMethods = false)
public class BenchmarkExecutorConfig {

    public static final int QUEUE_CAPACITY = 4;

    @Bean(name = "benchmarkExecutor")
    ThreadPoolTaskExecutor benchmarkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("benchmark-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
