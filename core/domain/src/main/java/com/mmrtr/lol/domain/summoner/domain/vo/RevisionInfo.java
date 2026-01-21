package com.mmrtr.lol.domain.summoner.domain.vo;

import java.time.LocalDateTime;

public record RevisionInfo(
        LocalDateTime revisionDate,
        LocalDateTime revisionClickDate
) {
}
