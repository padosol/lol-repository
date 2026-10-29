package com.mmrtr.lol.infra.rabbitmq.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SummonerMessage {

    private String platformId;
    private String puuid;
    private long revisionDate;
    /** 티어 수집 등 사용자 요청이 아닌 갱신이면 true. 필드가 없는 메시지(lol-server 등)는 사용자 갱신으로 본다. */
    private boolean background;

    public SummonerMessage(String platformId, String puuid, long revisionDate) {
        this(platformId, puuid, revisionDate, false);
    }
}
