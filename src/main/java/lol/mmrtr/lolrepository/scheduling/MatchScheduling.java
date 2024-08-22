package lol.mmrtr.lolrepository.scheduling;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lol.mmrtr.lolrepository.bucket.BucketService;
import lol.mmrtr.lolrepository.kafka_consumer.MatchIdConsumer;
import lol.mmrtr.lolrepository.kafka_consumer.exception.MatchIdConsumerActiveException;
import lol.mmrtr.lolrepository.redis.model.MatchSession;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lol.mmrtr.lolrepository.service.MatchService;
import lol.mmrtr.lolrepository.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduling {

    private final RedisTemplate<String, Object> redisTemplate;

    private final BucketService bucketService;

    private final MatchService matchService;

    private final TimelineService timelineService;

    private final ObjectMapper objectMapper = new ObjectMapper();

//    @Async(value = "schedulerTask")
    @Scheduled(fixedRate = 1000)
//    @Retryable(retryFor = {MatchIdConsumerActiveException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void run() throws IOException {

        if(!MatchIdConsumer.active) {
            ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();

            Bucket platformBucket = bucketService.getBucket(BucketService.BucketKey.PLATFORM_PLATFORM);

            List<String> matchIds = zSet.range("matchId", 0, 20).stream().map(mathId -> (String) mathId).toList();

            List<CompletableFuture<MatchDto>> matchDtoFutureList = new ArrayList<>();
            List<CompletableFuture<TimelineDto>> timelineFutureList = new ArrayList<>();

            for (String matchId : matchIds) {

                ConsumptionProbe probe = platformBucket.tryConsumeAndReturnRemaining(2);
                if(probe.isConsumed()) {

                    log.info("MatchId: {} 요청, App 남은 토큰 수: {}", (String) matchId, probe.getRemainingTokens());
                    zSet.remove("matchId", matchId);

                    Platform platform = Platform.valueOfName(matchId.split("_")[0]);

                    CompletableFuture<TimelineDto> timelineDtoFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return RiotAPI.timeLine(platform).byMatchIdFuture(matchId).get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    CompletableFuture<MatchDto> matchDtoFuture = CompletableFuture.supplyAsync(() -> {

                        try {
                            return RiotAPI.match(platform).byMatchIdFuture(matchId).get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    timelineFutureList.add(timelineDtoFuture);
                    matchDtoFutureList.add(matchDtoFuture);
                }
            }

            List<MatchDto> matchDtoList = matchDtoFutureList.stream().map(CompletableFuture::join).toList();
            matchService.bulkSave(matchDtoList);

            List<TimelineDto> timelineDtoList = timelineFutureList.stream().map(CompletableFuture::join).toList();
            timelineService.bulkSave(timelineDtoList);
        } else {
            log.warn("MatchIdConsumer 동작중.... 대기");
        }


    }

}
