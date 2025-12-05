package com.mmrtr.lolrepository.bucket.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.SynchronizationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class BucketConfig {

    @Bean
    public Bucket bucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(490)
                .refillGreedy(490, Duration.ofSeconds(10))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .withSynchronizationStrategy(SynchronizationStrategy.SYNCHRONIZED)
                .build();
    }
}
