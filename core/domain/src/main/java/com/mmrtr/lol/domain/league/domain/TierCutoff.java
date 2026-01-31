package com.mmrtr.lol.domain.league.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TierCutoff {
    private Long id;
    private String queue;
    private String tier;
    private int minLeaguePoints;
    private LocalDateTime updatedAt;
}
