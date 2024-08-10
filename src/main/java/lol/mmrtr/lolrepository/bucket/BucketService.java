package lol.mmrtr.lolrepository.bucket;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BucketService {

    private final ProxyManager<String> proxyManager;

    public Bucket getBucket(BucketKey key) {
        return proxyManager.getProxy(key.name(), bucketConfiguration(key));
    }

    private Supplier<BucketConfiguration> bucketConfiguration(BucketKey key) {
        return () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(key.getMaxRequestCount()).refillIntervally(key.getMaxRequestCount(), key.getDuration()))
                .build();
    }

    @Getter
    public enum BucketKey{
        MATCH_V5_MATCHES(2000, Duration.ofSeconds(10)),
        MATCH_V5_MATCHES_IDS(2000, Duration.ofSeconds(10)),
        MATCH_V5_TIMELINE(2000, Duration.ofSeconds(10)),
        LEAGUE_V4_BY_QUEUE(30, Duration.ofSeconds(10)), LEAGUE_V4_BY_SUMMONER(100, Duration.ofMinutes(1)),
        SUMMONER_V4_BY_PUUID(265, Duration.ofSeconds(10)), SUMMONER_V4_BY_ACCOUNT(265, Duration.ofSeconds(10)),

        PLATFORM_REGION(500, Duration.ofSeconds(10)),
        PLATFORM_PLATFORM(500, Duration.ofSeconds(10))
        ;

        private int maxRequestCount;
        private Duration duration;

        BucketKey(int maxRequestCount, Duration duration) {
            this.maxRequestCount = maxRequestCount;
            this.duration = duration;
        }


    }

}
