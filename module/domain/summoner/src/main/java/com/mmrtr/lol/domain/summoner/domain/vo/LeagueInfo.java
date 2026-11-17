package com.mmrtr.lol.domain.summoner.domain.vo;

import com.mmrtr.lol.common.type.Queue;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Arrays;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LeagueInfo {
    private String leagueId;
    private String queueType;
    private String tier;
    private String rank;
    private int leaguePoints;
    private int wins;
    private int losses;
    private boolean hotStreak;
    private boolean veteran;
    private boolean freshBlood;
    private boolean inactive;

    /**
     * 랭크 티어 체계({@link Queue})를 쓰는 큐인지. Riot league-v4 는 Arena({@code CHERRY}, WOOD~GLADIATOR) 처럼
     * 티어 체계가 다른 큐도 돌려주므로, 지원 큐가 아니면 적재하지 않는다 — 새 큐가 생겨도 기본은 무시.
     * {@code is*}/{@code get*} 이름이 아닌 건 응답 JSON({@code SummonerResponse.leagueInfos})에 필드로 잡히지 않게 하려는 것.
     */
    public boolean belongsToSupportedQueue() {
        return Arrays.stream(Queue.values()).anyMatch(queue -> queue.name().equals(queueType));
    }
}
