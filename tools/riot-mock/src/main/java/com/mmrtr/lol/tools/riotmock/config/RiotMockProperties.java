package com.mmrtr.lol.tools.riotmock.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("riot-mock")
public record RiotMockProperties(
        @DefaultValue("500:10") String appLimit,
        @DefaultValue Map<String, String> methodLimits,
        @DefaultValue("1000:10") String defaultMethodLimit,
        @DefaultValue("true") boolean countRejected,
        @DefaultValue("true") boolean apiKeyRequired,
        @DefaultValue Latency latency,
        @DefaultValue World world) {

    public record Latency(
            @DefaultValue("true") boolean enabled,
            @DefaultValue Map<String, Endpoint> endpoints) {
    }

    public record Endpoint(long meanMs, long jitterMs) {
    }

    public record World(
            @DefaultValue("4000") int players,
            @DefaultValue("2500") int firstVisitMax,
            @DefaultValue("60") int baseHistory,
            @DefaultValue({"30", "40", "20", "10"}) List<Integer> newGameWeights,
            String gameVersion) {
    }
}
