package lol.mmrtr.lolrepository.riot.core.builder.champion;

import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.champion.ChampionInfo;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.ExecutionException;

@Slf4j
public class Champion {

    private Platform platform;

    public Champion(Platform platform) {
        this.platform = platform;
    }


    public static class Builder {

        private Platform platform;

        public ChampionInfo get()  {

            try {
                UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
                builder.scheme("https").host(this.platform.getPlatform() + ".api.riotgames.com");
                builder.path("/lol/platform/v3/champion-rotations");

                return RiotAPI.getExecute().execute(ChampionInfo.class, builder.build().toUri()).get();
            } catch(ExecutionException | InterruptedException e) {
                e.printStackTrace();
                log.info("API ERROR");
                return null;
            }

        }

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

    }


    public ChampionInfo getChampionRotation() {
        return new Builder().platform(this.platform).get();
    }

}
