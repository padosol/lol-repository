package com.mmrtr.lolrepository.riot.core.builder.account;

import com.mmrtr.lolrepository.riot.core.api.RiotAPI;
import com.mmrtr.lolrepository.riot.dto.account.AccountDto;
import com.mmrtr.lolrepository.riot.type.Platform;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Account {

    private Platform platform;

    public Account(Platform platform) {
        this.platform = platform;
    }

    public static class Builder {

        private Platform platform;
        private String puuid;
        private String gameName;
        private String tagLine;

        public Builder(String puuid) {
            this.puuid = puuid;
        }

        public Builder(String gameName, String tagLine) {
            this.gameName = gameName;
            this.tagLine = tagLine;
        }

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public AccountDto get() {
            try {
                return getLazy().get();
            } catch(ExecutionException | InterruptedException e) {
                return new AccountDto();
            }
        }

        public CompletableFuture<AccountDto> getLazy() {

            if(this.platform == null) {

                Platform defaultPlatform = RiotAPI.getPlatform();

                if(defaultPlatform == null) {
                    throw new IllegalStateException("Default Platform 이 존재하지 않습니다.");
                }

                this.platform = defaultPlatform;
            }

            UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
            builder.scheme("https").host(this.platform.getPlatform() + ".api.riotgames.com");

            if(StringUtils.hasText(this.puuid)) {
                builder.path("riot/account/v1/accounts/by-puuid/" + this.puuid);
            } else if (StringUtils.hasText(this.gameName) && StringUtils.hasText(this.tagLine)) {
                builder.path("riot/account/v1/accounts/by-riot-id/" + this.gameName + "/" + this.tagLine);
            } else {
                throw new IllegalStateException("Account Path 가 존재하지 않습니다.");
            }

            return RiotAPI.getExecute().execute(AccountDto.class, builder.encode(StandardCharsets.UTF_8).build().toUri());
        }

    }

    public AccountDto byPuuid(String puuid) {
        return new Builder(puuid).platform(this.platform).get();
    }

    public CompletableFuture<AccountDto> byPuuidLazy(String puuid) {
        return new Builder(puuid).platform(this.platform).getLazy();
    }

    public AccountDto byRiotId(String gameName, String tagLine) {
        return new Builder(gameName, tagLine).platform(this.platform).get();
    }

}
