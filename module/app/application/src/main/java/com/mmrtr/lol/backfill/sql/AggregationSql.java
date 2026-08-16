package com.mmrtr.lol.backfill.sql;

import com.mmrtr.lol.backfill.BackfillFactType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Component
public class AggregationSql {

    private final Map<BackfillFactType, String> cache;

    public AggregationSql() {
        Map<BackfillFactType, String> map = new EnumMap<>(BackfillFactType.class);
        for (BackfillFactType type : BackfillFactType.values()) {
            map.put(type, load(type.sqlResourcePath()));
        }
        this.cache = map;
    }

    public String get(BackfillFactType factType) {
        return cache.get(factType);
    }

    private static String load(String resourcePath) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(resourcePath).getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load aggregation SQL resource: " + resourcePath, e);
        }
    }
}
