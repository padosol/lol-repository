package com.mmrtr.lol.controller.admin.request;

public record ItemMetadataRequest(
        int itemId,
        String itemName,
        String itemCategory,
        String gameVersion
) {
}
