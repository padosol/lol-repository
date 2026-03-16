package com.mmrtr.lol.infra.riot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "requestExecutor", destroyMethod = "close")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorService virtualRequestExecutor() {
        return newVirtualThreadExecutor("request-vt-", 0);
    }

    @Bean(name = "requestExecutor")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "false")
    public Executor platformRequestExecutor() {
        return newPlatformThreadPoolExecutor(20, 40, 100, "Request Thread-");
    }

    @Bean(name = "matchFindExecutor", destroyMethod = "close")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorService virtualMatchFindExecutor() {
        return newVirtualThreadExecutor("match-find-vt-", 0);
    }

    @Bean(name = "matchFindExecutor")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "false")
    public Executor platformMatchFindExecutor() {
        return newPlatformThreadPoolExecutor(5, 10, 20, "MatchFind Thread-");
    }

    @Bean(name = "riotApiExecutor", destroyMethod = "close")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorService virtualRiotApiExecutor() {
        return newVirtualThreadExecutor("riot-api-vt-", 0);
    }

    @Bean(name = "riotApiExecutor")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "false")
    public Executor platformRiotApiExecutor() {
        return newPlatformThreadPoolExecutor(20, 40, 40, "Riot API Thread-");
    }

    @Bean
    public Executor timelineSaveExecutor() {
        ThreadPoolTaskExecutor executor = newPlatformThreadPoolExecutor(10, 10, 0, "TimelineSave-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    private ExecutorService newVirtualThreadExecutor(String prefix, long startIndex) {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name(prefix, startIndex)
                        .factory()
        );
    }

    private ThreadPoolTaskExecutor newPlatformThreadPoolExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
