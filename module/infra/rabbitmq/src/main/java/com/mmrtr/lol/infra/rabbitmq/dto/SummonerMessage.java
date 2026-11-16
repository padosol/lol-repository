package com.mmrtr.lol.infra.rabbitmq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummonerMessage {

    private String platformId;
    private String puuid;
    private long revisionDate;
    /** 사용자 갱신 하나를 가리키는 id. 없으면(배포 순간 남은 옛 메시지) 낡은 메시지로 본다. */
    private String renewalId;
    /** 소환사 단계의 429 재시도 횟수. 필드가 없는 메시지는 0. */
    private int attempt;

    public SummonerMessage(String platformId, String puuid, long revisionDate, String renewalId, int attempt) {
        this.platformId = platformId;
        this.puuid = puuid;
        this.revisionDate = revisionDate;
        this.renewalId = renewalId;
        this.attempt = attempt;
    }

    /** 시도 수만 하나 올린 같은 메시지. */
    public SummonerMessage nextAttempt() {
        return new SummonerMessage(platformId, puuid, revisionDate, renewalId, attempt + 1);
    }
}
