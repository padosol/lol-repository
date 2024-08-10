package lol.mmrtr.lolrepository.riot.core.builder.match;


import io.github.bucket4j.Bucket;
import lol.mmrtr.lolrepository.redis.model.MatchSession;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.util.UriComponentsBuilder;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class Match {

    private Platform platform;

    public Match(Platform platform) {
        this.platform = platform;
    }

    public static class Builder{
        private Platform platform;
        private String matchId;
        private List<String> matchIds;

        public Builder(String matchId) {
            this.matchId = matchId;
        }

        public Builder(List<String> matchIds) {
            this.matchIds = matchIds;
        }

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public MatchDto get()  {

            try {
                MatchDto matchDto = get(this.matchId).get();
                return matchDto;
            } catch(ExecutionException | InterruptedException e) {
                e.printStackTrace();
                log.info("API ERROR");
                return null;
            }

        }

        public CompletableFuture<MatchDto> getFuture(String matchId) {
            return get(matchId);
        }

        private CompletableFuture<MatchDto> get(String matchId) {

            UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
            builder.scheme("https").host(this.platform.getPlatform() + ".api.riotgames.com");
            builder.path("/lol/match/v5/matches/" + matchId);

            return RiotAPI.getExecute().execute(MatchDto.class, builder.build().toUri());
        }

        private List<MatchDto> getAll()  {

            List<CompletableFuture<MatchDto>> matchList = new ArrayList<>();

            for(String matchId : this.matchIds) {
                CompletableFuture<MatchDto> future = get(matchId);
                matchList.add(future);
            }

            return matchList.stream().map(CompletableFuture::join).toList();
        }

    }
    public CompletableFuture<MatchDto> byMatchIdFuture(String matchId) {
        return new Builder(matchId).platform(this.platform).getFuture(matchId);
    }


    public MatchDto byMatchId(String matchId) {
        return new Builder(matchId).platform(this.platform).get();
    }

    public List<MatchDto> byMatchIds(List<String> matchIds) {
        return new Builder(matchIds).platform(this.platform).getAll();
    }

}
