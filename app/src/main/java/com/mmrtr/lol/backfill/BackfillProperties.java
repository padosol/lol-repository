package com.mmrtr.lol.backfill;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "lol.backfill.match-participant-build")
public record BackfillProperties(
        Range range,
        Chunk chunk,
        Filter filter,
        Gcs gcs
) {

    public record Range(long startId, long endId) {
    }

    public record Chunk(int size, int parallelism, int fetchSize) {
    }

    public record Filter(int season, List<Integer> queueIds) {
    }

    public record Gcs(String bucket, String prefix) {
    }
}
