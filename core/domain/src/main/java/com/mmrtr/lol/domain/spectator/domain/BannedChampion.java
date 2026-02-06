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
public class BannedChampion {
    private long championId;
    private long teamId;
    private int pickTurn;
}
