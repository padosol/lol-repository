package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.infra.riot.aspect.RateLimitType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

@Component
public class HostRateLimitResolver {

    private static final Set<String> REGIONAL_HOSTS = Set.of(
            "americas.api.riotgames.com",
            "europe.api.riotgames.com",
            "asia.api.riotgames.com",
            "sea.api.riotgames.com"
    );

    public RateLimitType resolve(URI uri) {
        return REGIONAL_HOSTS.contains(uri.getHost())
                ? RateLimitType.REGION_RATE_LIMITER
                : RateLimitType.PLATFORM_RATE_LIMITER;
    }
}
