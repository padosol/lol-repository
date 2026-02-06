package com.mmrtr.lol.domain.spectator.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GameParticipant {
    private String riotId;
    private String puuid;
    private long championId;
    private long teamId;
    private long spell1Id;
    private long spell2Id;
    private boolean bot;
    private int lastSelectedSkinIndex;
    private long profileIconId;
    private GamePerks perks;
}
