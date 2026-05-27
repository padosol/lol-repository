package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemSeqView {
    private int itemId;
    private long minute;
    private String type;
}
