package com.mmrtr.lol.infra.rabbitmq.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SummonerMessage {

    private String platformId;
    private String puuid;
    private long revisionDate;
}
