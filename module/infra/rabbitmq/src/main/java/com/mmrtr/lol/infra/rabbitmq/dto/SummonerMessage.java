package com.mmrtr.lol.infra.rabbitmq.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class SummonerMessage {

    private String platformId;
    private String puuid;
    private long revisionDate;
    /** 티어 수집 등 사용자 요청이 아닌 갱신이면 true. 필드가 없는 메시지(lol-server 등)는 사용자 갱신으로 본다. */
    private boolean background;
    /** 사용자 갱신 하나를 가리키는 id. 티어 수집 등 갱신 레코드가 없는 메시지는 null. */
    private String renewalId;
    /** 계정 단계의 429 재시도 횟수. 필드가 없는 메시지는 0. */
    private int attempt;

    public SummonerMessage(String platformId, String puuid, long revisionDate) {
        this(platformId, puuid, revisionDate, false);
    }

    public SummonerMessage(String platformId, String puuid, long revisionDate, boolean background) {
        this.platformId = platformId;
        this.puuid = puuid;
        this.revisionDate = revisionDate;
        this.background = background;
    }

    public SummonerMessage(String platformId, String puuid, long revisionDate, String renewalId, int attempt) {
        this(platformId, puuid, revisionDate, false);
        this.renewalId = renewalId;
        this.attempt = attempt;
    }

    /** 시도 수만 하나 올린 같은 메시지. */
    public SummonerMessage nextAttempt() {
        SummonerMessage next = new SummonerMessage(platformId, puuid, revisionDate, renewalId, attempt + 1);
        next.background = background;
        return next;
    }
}
