package com.mmrtr.lol.infra.rabbitmq.dto;

public record SummonerRenewalMessage(
        String puuid,
        String platform,
        long revisionDate
) {
}
