package com.mmrtr.lol.riot.core.builder.match;

import com.mmrtr.lol.riot.core.api.RiotAPI;
import com.mmrtr.lol.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.riot.type.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class TimeLine {

    private Platform platform;

    public TimeLine(Platform platform) {
        this.platform = platform;
    }

    public static class Builder{
        private Platform platform;
        private String matchId;
        private Collection<String> matchIds;

        public Builder(String matchId) {
            this.matchId = matchId;
        }

        public Builder(Collection<String> matchIds) {
            this.matchIds = matchIds;
        }

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public TimelineDto get()  {

            try {
                return get(this.matchId).get();
            } catch(ExecutionException | InterruptedException e) {
                e.printStackTrace();
                log.info("API ERROR");
                return null;
            }

        }

        private CompletableFuture<TimelineDto> get(String matchId) {

            UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
            builder.scheme("https").host(this.platform.getPlatform() + ".api.riotgames.com");
            builder.path("/lol/match/v5/matches/" + matchId + "/timeline");

            return RiotAPI.getExecute().execute(TimelineDto.class, builder.build().toUri());
        }


        // 수정 필요함
        private List<TimelineDto> getAll()  {

            List<CompletableFuture<TimelineDto>> timelineList = new ArrayList<>();

            int i = 0;
            for(String matchId : this.matchIds) {
                CompletableFuture<TimelineDto> future = get(matchId);
                timelineList.add(future);
            }

            return timelineList.stream().map(CompletableFuture::join).toList();
        }

        public CompletableFuture<TimelineDto> getFuture(String matchId) {
            return get(matchId);
        }

    }

    public CompletableFuture<TimelineDto> byMatchIdFuture(String matchId) {
        return new Builder(matchId).platform(this.platform).getFuture(matchId);
    }

    public TimelineDto byMatchId(String matchId) {
        return new Builder(matchId).platform(this.platform).get();
    }

    public List<TimelineDto> byMatchIds(Collection<String> matchIds) {
        return new Builder(matchIds).platform(this.platform).getAll();
    }

}
