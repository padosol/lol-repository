package com.mmrtr.lol.domain.champion_stat.domain;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ItemMetadata {

    private int itemId;
    private String itemName;
    private String itemCategory;
    private String gameVersion;
}
