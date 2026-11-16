package com.mmrtr.lol.support.riot;

/**
 * Riot API 호출 한 번에 대한 허가. token 은 허가 기록의 키다.
 */
public record RiotPermit(String token, String platform, RiotMethod method, RiotCallPriority priority) {
}
