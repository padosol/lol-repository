package lol.mmrtr.lolrepository.kafka_consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lol.mmrtr.lolrepository.bucket.BucketService;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchIdConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BucketService bucketService;
    public static boolean active = false;
    private final MatchService matchService;
    private final TimelineService timelineService;

//    @KafkaListener(topics = "matchId", groupId = "group_1", containerFactory = "kafkaListenerContainerFactory")
    public void listener0(
        List<String> matchIds,
        Acknowledgment ack
    ) {
        active = true;

        try {
            ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();
            Bucket platformBucket = bucketService.getBucket(BucketService.BucketKey.PLATFORM_REGION);
    
            List<CompletableFuture<MatchDto>> matchDtoFutureList = new ArrayList<>();
            List<CompletableFuture<TimelineDto>> timelineFutureList = new ArrayList<>();
    
            for (String matchId : matchIds) {
    
                ConsumptionProbe probe = platformBucket.tryConsumeAndReturnRemaining(2);
                if(probe.isConsumed()) {
    
                    log.info("MatchId: {} 요청, App 남은 토큰 수: {}", matchId, probe.getRemainingTokens());
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
            List<TimelineDto> timelineDtoList = timelineFutureList.stream().map(CompletableFuture::join).toList();

            matchService.bulkSave(matchDtoList);
            timelineService.bulkSave(timelineDtoList);

            ack.acknowledge();
        } catch(Exception e) {
            log.warn("카프카 에러 발생");
            active = false;
        } finally {
            active = false;
        }

    }
}
