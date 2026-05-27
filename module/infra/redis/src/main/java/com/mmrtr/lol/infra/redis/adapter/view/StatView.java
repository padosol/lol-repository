package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StatView {
    private int defense;
    private int flex;
    private int offense;
}
