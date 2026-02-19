package com.mmrtr.lol.controller.admin.request;

import com.mmrtr.lol.common.type.ItemCategory;

public record ItemMetadataRequest(
        int itemId,
        String itemName,
        ItemCategory itemCategory,
        String gameVersion
) {
}
