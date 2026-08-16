package com.mmrtr.lol.backfill.config;

import com.mmrtr.lol.backfill.BackfillProperties;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(BackfillProperties.class)
@RequiredArgsConstructor
public class BackfillJobConfig {

    public static final String JOB_NAME = "lolBackfillJob";
    private static final String PARTITION_STEP = "backfillPartitionStep";
    private static final String CHUNK_STEP = "backfillChunkStep";
    private static final String MANIFEST_STEP = "backfillManifestStep";
    private static final String WORKER_PREFIX = "lol-backfill-";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job lolBackfillJob(Step backfillPartitionStep, Step backfillManifestStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(backfillPartitionStep)
                .next(backfillManifestStep)
                .build();
    }

    @Bean
    public Step backfillPartitionStep(
            IdRangePartitioner idRangePartitioner,
            Step backfillChunkStep,
            TaskExecutor backfillTaskExecutor
    ) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setTaskExecutor(backfillTaskExecutor);
        handler.setStep(backfillChunkStep);

        return new StepBuilder(PARTITION_STEP, jobRepository)
                .partitioner(CHUNK_STEP, idRangePartitioner)
                .partitionHandler(handler)
                .build();
    }

    @Bean
    public Step backfillChunkStep(ChunkExportTasklet chunkExportTasklet) {
        return new StepBuilder(CHUNK_STEP, jobRepository)
                .tasklet(chunkExportTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step backfillManifestStep(ManifestTasklet manifestTasklet) {
        return new StepBuilder(MANIFEST_STEP, jobRepository)
                .tasklet(manifestTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public IdRangePartitioner idRangePartitioner(
            BackfillProperties properties,
            @Value("#{jobParameters['startId']}") Long overrideStartId,
            @Value("#{jobParameters['endId']}") Long overrideEndId
    ) {
        long startId = overrideStartId != null ? overrideStartId : properties.range().startId();
        long endId = overrideEndId != null ? overrideEndId : properties.range().endId();
        return new IdRangePartitioner(startId, endId, properties.chunk().size());
    }

    @Bean
    @JobScope
    public TaskExecutor backfillTaskExecutor(BackfillProperties properties) {
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

    @Bean("backfillJobLauncher")
    public JobLauncher backfillJobLauncher() throws Exception {
        ThreadPoolTaskExecutor launcherExecutor = new ThreadPoolTaskExecutor();
        launcherExecutor.setCorePoolSize(1);
        launcherExecutor.setMaxPoolSize(1);
        launcherExecutor.setQueueCapacity(4);
        launcherExecutor.setThreadNamePrefix("lol-backfill-launcher-");
        launcherExecutor.initialize();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(launcherExecutor);
        launcher.afterPropertiesSet();
        return launcher;
    }
}
