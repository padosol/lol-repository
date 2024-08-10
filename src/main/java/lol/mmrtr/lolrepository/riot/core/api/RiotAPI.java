package lol.mmrtr.lolrepository.riot.core.api;


import io.github.bucket4j.Bucket;
import lol.mmrtr.lolrepository.bucket.BucketService;
import lol.mmrtr.lolrepository.riot.core.builder.account.Account;
import lol.mmrtr.lolrepository.riot.core.builder.champion.Champion;
import lol.mmrtr.lolrepository.riot.core.builder.league.League;
import lol.mmrtr.lolrepository.riot.core.builder.match.Match;
import lol.mmrtr.lolrepository.riot.core.builder.match.MatchList;
import lol.mmrtr.lolrepository.riot.core.builder.match.TimeLine;
import lol.mmrtr.lolrepository.riot.core.builder.spectator.Spactator;
import lol.mmrtr.lolrepository.riot.core.builder.summoner.Summoner;
import lol.mmrtr.lolrepository.riot.core.calling.RiotExecute;
import lol.mmrtr.lolrepository.riot.dto.champion.ChampionInfo;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.ExecutionException;

@Slf4j
@Getter
public class RiotAPI {

    public static final String DEFAULT_HOST = ".api.riotgames.com";
    private static RiotExecute defaultRiotExecute;
    private static Platform platform = Platform.KR;
    public final String API_KEY;
    private static RedisTemplate<String, Object> REDISTEMPLATE;
    private static BucketService BUCKET_SERVICE;

    public RiotAPI(String apiKey, RiotExecute execute, RedisTemplate<String, Object> redisTemplate, BucketService bucketService) {
        API_KEY = apiKey;
        defaultRiotExecute = execute;
        REDISTEMPLATE = redisTemplate;
        BUCKET_SERVICE = bucketService;
    }


    public static void setRiotExecute(RiotExecute riotExecute) {
        defaultRiotExecute = riotExecute;
    }

    public static RiotApiBuilder builder() {
        return new RiotApiBuilder();
    }

    public static class RiotApiBuilder{

        private RedisTemplate<String, Object> redisTemplate;
        private RiotExecute execute;
        private BucketService bucketService;
        private String apiKey;

        public RiotApiBuilder apiKey(String apiKey) {

            if(!StringUtils.hasText(apiKey)) {
                throw new IllegalStateException("Riot API-KEY 를 등록해주세요.");
            }

            this.apiKey = apiKey;
            return this;
        }

        public RiotApiBuilder execute(RiotExecute execute) {
            this.execute = execute;
            return this;
        }

        public RiotApiBuilder redisTemplate(RedisTemplate<String, Object> redisTemplate) {
            this.redisTemplate = redisTemplate;
            return this;
        }

        public RiotApiBuilder bucket(BucketService bucketService) {
            this.bucketService = bucketService;
            return this;
        }

        public RiotAPI build()  {

            UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
            builder.scheme("https").host(Platform.KR.getRegion() + ".api.riotgames.com");
            builder.path("/lol/platform/v3/champion-rotations");

            try {
                ChampionInfo championInfo = execute.execute(ChampionInfo.class, builder.build().toUri()).get();
                if(championInfo.isError()) {
                    throw new IllegalStateException("유효하지 않은 API_KEY 입니다.");
                }

            } catch(ExecutionException | InterruptedException e) {
                throw new RuntimeException();
            }

            return new RiotAPI(this.apiKey, execute, this.redisTemplate, this.bucketService);
        }
    }

    public static Account account(Platform platform) {return new Account(platform);}

    public static Champion champion(Platform platform) {return new Champion(platform);}

    public static League league(Platform platform) {return new League(platform);}

    public static Match match(Platform platform) {return new Match(platform);}

    public static TimeLine timeLine(Platform platform) {return new TimeLine(platform);}

    public static MatchList matchList(Platform platform) {return new MatchList(platform);}

    public static Spactator spactator() {return new Spactator();}

    public static Summoner summoner(Platform platform) {return new Summoner(platform);}

    public static Platform getPlatform() {
        return platform;
    }

    public static RiotExecute getExecute() {return defaultRiotExecute;}

    public static RedisTemplate<String, Object> getRedistemplate() {return REDISTEMPLATE;}

    public static String createRegionPath(Platform platform) {
        return platform.getRegion() + DEFAULT_HOST;
    }

    public static String createCountryPath(Platform platform) {
        return platform.getPlatform() + DEFAULT_HOST;
    }


}
