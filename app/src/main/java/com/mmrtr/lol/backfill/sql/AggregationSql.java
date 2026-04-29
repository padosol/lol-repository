package com.mmrtr.lol.backfill.sql;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AggregationSql {

    private static final String RESOURCE_PATH = "sql/match-participant-build-aggregation.sql";

    private final String sql;

    public AggregationSql() {
        try {
            this.sql = StreamUtils.copyToString(
                    new ClassPathResource(RESOURCE_PATH).getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load aggregation SQL resource: " + RESOURCE_PATH, e);
        }
    }

    public String get() {
        return sql;
    }
}
