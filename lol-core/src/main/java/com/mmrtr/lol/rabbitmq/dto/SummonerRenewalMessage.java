package com.mmrtr.lol.rabbitmq.dto;

public record SummonerRenewalMessage(
        String puuid,
        String platform,
        long revisionDate
) {
}
