package lol.mmrtr.lolrepository.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lol.mmrtr.lolrepository.bucket.BucketService;
import lol.mmrtr.lolrepository.redis.model.MatchRenewalSession;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lol.mmrtr.lolrepository.service.MatchService;
import lol.mmrtr.lolrepository.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriberListener implements MessageListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BucketService bucketService;
    private final MatchService matchService;
    private final TimelineService timelineService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            byte[] body = message.getBody();

            String s = objectMapper.readValue(body, String.class);

            MatchRenewalSession matchRenewalSession = objectMapper.readValue(s, MatchRenewalSession.class);
            List<String> matchIds = matchRenewalSession.getMatchIds();
            String puuid = matchRenewalSession.getPuuid();

            HashOperations<String, Object, Object> redisHash = redisTemplate.opsForHash();
            Map<Object, Object> entries = redisHash.entries(puuid);

            log.info("matchId: [{}]", matchIds.size());

            Bucket platformBucket = bucketService.getBucket(BucketService.BucketKey.PLATFORM_PLATFORM);

            List<CompletableFuture<MatchDto>> matchDtoFutureList = new ArrayList<>();
            List<CompletableFuture<TimelineDto>> timelineFutureList = new ArrayList<>();

            for (String matchId : matchIds) {

                ConsumptionProbe probe = platformBucket.tryConsumeAndReturnRemaining(2);
                if(probe.isConsumed()) {

                    log.info("MatchId: {} 요청, App 남은 토큰 수: {}", matchId, probe.getRemainingTokens());

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

            redisHash.put("summonerRenewal:" + puuid, "matchUpdate", 1);

        } catch(IOException e) {
            //
            e.printStackTrace();
        }

    }
}
