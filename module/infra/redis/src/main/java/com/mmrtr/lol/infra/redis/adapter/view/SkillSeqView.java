package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkillSeqView {
    private int skillSlot;
    private long minute;
    private String type;
}
