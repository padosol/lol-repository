package com.mmrtr.lol.infra.rabbitmq.dto;

/**
 * 추가 매치 찾기 메시지. attempt 는 일시 오류로 다시 넣은 횟수이고, JSON 에 없으면 0 이다.
 */
public record SummonerRenewalMessage(
        String puuid,
        String platform,
        long revisionDate,
        Integer startOffset,
        int attempt
) {

    private static final int DEFAULT_START_OFFSET = 20;

    public SummonerRenewalMessage(String puuid, String platform, long revisionDate) {
        this(puuid, platform, revisionDate, null, 0);
    }

    public SummonerRenewalMessage(String puuid, String platform, long revisionDate, Integer startOffset) {
        this(puuid, platform, revisionDate, startOffset, 0);
    }

    public int startOffsetOrDefault() {
        return startOffset == null ? DEFAULT_START_OFFSET : startOffset;
    }

    /** offset 부터 이어서 찾는 메시지. 시도 수는 그대로 둔다 (허가 거절). */
    public SummonerRenewalMessage fromOffset(int offset) {
        return new SummonerRenewalMessage(puuid, platform, revisionDate, offset, attempt);
    }

    /** offset 부터 이어서 찾고 시도 수를 하나 올린 메시지 (일시 오류). */
    public SummonerRenewalMessage nextAttempt(int offset) {
        return new SummonerRenewalMessage(puuid, platform, revisionDate, offset, attempt + 1);
    }
}
