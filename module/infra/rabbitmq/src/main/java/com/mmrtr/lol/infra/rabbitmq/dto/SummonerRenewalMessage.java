package com.mmrtr.lol.infra.rabbitmq.dto;

public record SummonerRenewalMessage(
        String puuid,
        String platform,
        long revisionDate,
        Integer startOffset
) {

    private static final int DEFAULT_START_OFFSET = 20;

    public SummonerRenewalMessage(String puuid, String platform, long revisionDate) {
        this(puuid, platform, revisionDate, null);
    }

    public int startOffsetOrDefault() {
        return startOffset == null ? DEFAULT_START_OFFSET : startOffset;
    }
}
