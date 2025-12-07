package com.mmrtr.lol.rabbitmq.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SummonerMessage {

    private String platform;
    private String puuid;
    private long revisionDate;
}
