package com.mmrtr.lol.infra.riot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean
    public Executor requestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);

        executor.setThreadNamePrefix("Request Thread-");
        executor.initialize();

        return executor;
    }

    @Bean
    public Executor riotApiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(40);

        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Riot API Thread-");
        executor.initialize();
        return executor;
    }
}
