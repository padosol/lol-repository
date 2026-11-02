package com.mmrtr.lol.tools.riotmock.ratelimit;

/** 버킷(예: "asia|app", "asia|m|match-v5.match") 한 창의 현재 사용량. */
public record BucketUsage(String bucket, int windowSeconds, int count, int limit) {
}
