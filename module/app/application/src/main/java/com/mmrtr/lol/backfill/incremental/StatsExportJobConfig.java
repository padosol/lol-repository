package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.partition.IdRangePartitioner;
import com.mmrtr.lol.backfill.tasklet.ChunkExportTasklet;
import com.mmrtr.lol.backfill.tasklet.ManifestTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 자동 증분 export Job. 기존 backfill 의 청크·매니페스트 tasklet 을 재사용하고 BigQuery 적재 스텝을 더한다.
 */
@Configuration
@EnableConfigurationProperties(StatsExportProperties.class)
@RequiredArgsConstructor
public class StatsExportJobConfig {

    public static final String JOB_NAME = "lolStatsExportJob";
    private static final String PARTITION_STEP = "statsExportPartitionStep";
    private static final String CHUNK_STEP = "statsExportChunkStep";
    private static final String MANIFEST_STEP = "statsExportManifestStep";
    private static final String BQ_LOAD_STEP = "statsExportBqLoadStep";
    private static final String WORKER_PREFIX = "lol-stats-export-";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job lolStatsExportJob(
            @Qualifier("statsExportPartitionStep") Step statsExportPartitionStep,
            @Qualifier("statsExportManifestStep") Step statsExportManifestStep,
            @Qualifier("statsExportBqLoadStep") Step statsExportBqLoadStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(statsExportPartitionStep)
                .next(statsExportManifestStep)
                .next(statsExportBqLoadStep)
                .build();
    }

    @Bean
    public Step statsExportPartitionStep(
            @Qualifier("statsExportIdRangePartitioner") IdRangePartitioner statsExportIdRangePartitioner,
            @Qualifier("statsExportChunkStep") Step statsExportChunkStep,
            @Qualifier("statsExportTaskExecutor") TaskExecutor statsExportTaskExecutor
    ) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setTaskExecutor(statsExportTaskExecutor);
        handler.setStep(statsExportChunkStep);

        return new StepBuilder(PARTITION_STEP, jobRepository)
                .partitioner(CHUNK_STEP, statsExportIdRangePartitioner)
                .partitionHandler(handler)
                .build();
    }

    @Bean
    public Step statsExportChunkStep(ChunkExportTasklet chunkExportTasklet) {
        return new StepBuilder(CHUNK_STEP, jobRepository)
                .tasklet(chunkExportTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step statsExportManifestStep(ManifestTasklet manifestTasklet) {
        return new StepBuilder(MANIFEST_STEP, jobRepository)
                .tasklet(manifestTasklet, transactionManager)
                .build();
    }

    /** BigQuery 대기 중 PG 커넥션을 잡지 않도록 리소스 없는 트랜잭션 매니저를 쓴다. */
    @Bean
    public Step statsExportBqLoadStep(BqLoadTasklet bqLoadTasklet) {
        return new StepBuilder(BQ_LOAD_STEP, jobRepository)
                .tasklet(bqLoadTasklet, new ResourcelessTransactionManager())
                .build();
    }

    @Bean
    @StepScope
    public IdRangePartitioner statsExportIdRangePartitioner(
            StatsExportProperties properties,
            @Value("#{jobParameters['startId']}") Long startId,
            @Value("#{jobParameters['endId']}") Long endId
    ) {
        return new IdRangePartitioner(startId, endId, properties.chunk().size());
    }

    @Bean
    @JobScope
    public TaskExecutor statsExportTaskExecutor(StatsExportProperties properties) {
        int parallelism = Math.max(1, properties.chunk().parallelism());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix(WORKER_PREFIX);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    public JobLauncher statsExportJobLauncher() throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SyncTaskExecutor());
        launcher.afterPropertiesSet();
        return launcher;
    }

    /** 스케줄러 트리거용. 동시에 1개만 돌리고 대기열은 두지 않는다(겹치면 거절). */
    @Bean
    public TaskExecutor statsExportTriggerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("lol-stats-export-trigger-");
        executor.initialize();
        return executor;
    }
}
