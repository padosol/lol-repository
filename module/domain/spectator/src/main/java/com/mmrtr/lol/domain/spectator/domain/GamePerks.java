package com.mmrtr.lol.domain.spectator.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GamePerks {
    private long perkStyle;
    private long perkSubStyle;
    private List<Long> perkIds;
}
