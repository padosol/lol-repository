package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StyleView {
    private int primaryStyleId;
    private int primaryPerk0;
    private int primaryPerk1;
    private int primaryPerk2;
    private int primaryPerk3;
    private int subStyleId;
    private int subPerk0;
    private int subPerk1;
}
