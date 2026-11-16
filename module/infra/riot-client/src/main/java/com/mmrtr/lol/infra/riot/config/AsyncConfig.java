package com.mmrtr.lol.infra.riot.config;

import com.mmrtr.lol.support.mdc.MdcAwareExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
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
        return newPlatformThreadPoolExecutor(10, 10, 0, "TimelineSave-", new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 갱신 매치 단계의 비동기 DB 저장({@code RenewalMatchSaver}). 동시 저장은 5개로 묶고, 20개까지 기다리게 한다.
     * 그보다 밀리면 넘기는 리스너가 자리가 날 때까지 기다린다(직접 저장하지 않아 상한을 넘지 않는다).
     */
    @Bean
    public Executor matchSaveExecutor() {
        return newPlatformThreadPoolExecutor(5, 5, 20, "MatchSave-", new BlockWhenFullPolicy());
    }

    private ExecutorService newVirtualThreadExecutor(String prefix, long startIndex) {
        ExecutorService delegate = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name(prefix, startIndex)
                        .factory()
        );
        return new MdcAwareExecutorService(delegate);
    }

    private ThreadPoolTaskExecutor newPlatformThreadPoolExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        return newPlatformThreadPoolExecutor(corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 거부 정책은 initialize 전에 둬야 실제 실행기에 들어간다 (뒤에 바꾸면 기본 AbortPolicy 그대로다). */
    private ThreadPoolTaskExecutor newPlatformThreadPoolExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        executor.initialize();
        return executor;
    }
}
