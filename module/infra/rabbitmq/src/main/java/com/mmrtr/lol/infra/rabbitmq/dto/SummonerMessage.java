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
}
