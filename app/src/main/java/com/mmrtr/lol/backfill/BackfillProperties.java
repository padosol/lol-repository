package com.mmrtr.lol.backfill;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "lol.backfill")
public record BackfillProperties(
        Range range,
        Chunk chunk,
        Filter filter,
        Gcs gcs
) {

    public record Range(Long startId, Long endId) {
    }

    public record Chunk(Integer size, Integer parallelism, Integer fetchSize) {
    }

    public record Filter(Integer season, List<Integer> queueIds) {
    }

    public record Gcs(String bucket) {
    }
}
