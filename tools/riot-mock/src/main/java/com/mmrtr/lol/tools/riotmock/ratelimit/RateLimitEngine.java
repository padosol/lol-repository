package com.mmrtr.lol.tools.riotmock.ratelimit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/** Riot 방식의 고정 창 한도 엔진. routing 별로 application 버킷과 method 버킷을 센다. */
public class RateLimitEngine {

    /** 거절 사유(X-Rate-Limit-Type) 값. */
    public static final String APPLICATION = "application";
    public static final String METHOD = "method";

    private final Map<String, String> methodLimits = new LinkedHashMap<>();
    private String defaultMethodLimit;
    private final boolean countRejected;
    private final LongSupplier clockMillis;
    // bucket 이름 -> (창 초 -> {시작ms, 수})
    private final Map<String, Map<Integer, long[]>> windows = new TreeMap<>();
    private String appLimit;

    public RateLimitEngine(String appLimit, Map<String, String> methodLimits, String defaultMethodLimit,
                           boolean countRejected, LongSupplier clockMillis) {
        LimitSpec.parseAll(appLimit);
        LimitSpec.parseAll(defaultMethodLimit);
        methodLimits.values().forEach(LimitSpec::parseAll);
        this.appLimit = appLimit;
        this.methodLimits.putAll(methodLimits);
        this.defaultMethodLimit = defaultMethodLimit;
        this.countRejected = countRejected;
        this.clockMillis = clockMillis;
    }

    public synchronized Verdict acquire(String routing, String endpointKey) {
        long now = clockMillis.getAsLong();
        String appBucket = routing + "|app";
        String methodBucket = routing + "|m|" + endpointKey;
        String methodLimit = methodLimitOf(endpointKey);
        List<LimitSpec> appSpecs = LimitSpec.parseAll(appLimit);
        List<LimitSpec> methodSpecs = LimitSpec.parseAll(methodLimit);

        long[] appFull = fullness(appBucket, appSpecs, now);
        long[] methodFull = fullness(methodBucket, methodSpecs, now);
        boolean appFullNow = appFull[0] > 0;
        boolean allowed = !appFullNow && methodFull[0] == 0;

        if (allowed || countRejected) {
            increment(appBucket, appSpecs);
            increment(methodBucket, methodSpecs);
        }

        String appCount = counts(appBucket, appSpecs);
        String methodCount = counts(methodBucket, methodSpecs);
        if (allowed) {
            return new Verdict(true, null, 0, appLimit, appCount, methodLimit, methodCount);
        }
        long retryMillis = Math.max(appFull[1], methodFull[1]);
        long retrySeconds = Math.max(1, (retryMillis + 999) / 1000);
        return new Verdict(false, appFullNow ? APPLICATION : METHOD, retrySeconds,
                appLimit, appCount, methodLimit, methodCount);
    }

    /** 구현하지 않은 경로용: 그 routing 의 application 버킷만 센다. method 헤더는 null 이다. */
    public synchronized Verdict acquireAppOnly(String routing) {
        long now = clockMillis.getAsLong();
        String appBucket = routing + "|app";
        List<LimitSpec> appSpecs = LimitSpec.parseAll(appLimit);
        long[] appFull = fullness(appBucket, appSpecs, now);
        boolean allowed = appFull[0] == 0;
        if (allowed || countRejected) {
            increment(appBucket, appSpecs);
        }
        String appCount = counts(appBucket, appSpecs);
        if (allowed) {
            return new Verdict(true, null, 0, appLimit, appCount, null, null);
        }
        return new Verdict(false, APPLICATION, Math.max(1, (appFull[1] + 999) / 1000), appLimit, appCount, null, null);
    }

    /** 창을 열거나 갱신하고, {가득 찬 창이 있으면 1, 찬 창들의 남은 ms 최댓값} 을 돌려준다. */
    private long[] fullness(String bucket, List<LimitSpec> specs, long now) {
        Map<Integer, long[]> byWindow = windows.computeIfAbsent(bucket, k -> new HashMap<>());
        long full = 0;
        long remaining = 0;
        for (LimitSpec spec : specs) {
            long[] w = byWindow.get(spec.windowSeconds());
            if (w == null || now >= w[0] + spec.windowSeconds() * 1000L) {
                w = new long[] {now, 0};
                byWindow.put(spec.windowSeconds(), w);
            }
            if (w[1] >= spec.limit()) {
                full = 1;
                remaining = Math.max(remaining, w[0] + spec.windowSeconds() * 1000L - now);
            }
        }
        return new long[] {full, remaining};
    }

    private void increment(String bucket, List<LimitSpec> specs) {
        Map<Integer, long[]> byWindow = windows.get(bucket);
        for (LimitSpec spec : specs) {
            byWindow.get(spec.windowSeconds())[1]++;
        }
    }

    private String counts(String bucket, List<LimitSpec> specs) {
        Map<Integer, long[]> byWindow = windows.get(bucket);
        List<String> parts = new ArrayList<>();
        for (LimitSpec spec : specs) {
            parts.add(byWindow.get(spec.windowSeconds())[1] + ":" + spec.windowSeconds());
        }
        return String.join(",", parts);
    }

    private String methodLimitOf(String endpointKey) {
        return methodLimits.getOrDefault(endpointKey, defaultMethodLimit);
    }

    public synchronized void setAppLimit(String limit) {
        LimitSpec.parseAll(limit);
        this.appLimit = limit;
    }

    public synchronized void setMethodLimit(String endpointKey, String limit) {
        LimitSpec.parseAll(limit);
        methodLimits.put(endpointKey, limit);
    }

    public synchronized String appLimit() {
        return appLimit;
    }

    public synchronized Map<String, String> methodLimits() {
        return Map.copyOf(methodLimits);
    }

    public synchronized String defaultMethodLimit() {
        return defaultMethodLimit;
    }

    public synchronized void setDefaultMethodLimit(String limit) {
        LimitSpec.parseAll(limit);
        this.defaultMethodLimit = limit;
    }

    public synchronized List<BucketUsage> usage() {
        long now = clockMillis.getAsLong();
        List<BucketUsage> result = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, long[]>> entry : windows.entrySet()) {
            String bucket = entry.getKey();
            for (LimitSpec spec : LimitSpec.parseAll(limitOfBucket(bucket))) {
                long[] w = entry.getValue().get(spec.windowSeconds());
                if (w != null) {
                    boolean expired = now >= w[0] + spec.windowSeconds() * 1000L;
                    result.add(new BucketUsage(bucket, spec.windowSeconds(), expired ? 0 : (int) w[1], spec.limit()));
                }
            }
        }
        return result;
    }

    private String limitOfBucket(String bucket) {
        if (bucket.endsWith("|app")) {
            return appLimit;
        }
        return methodLimitOf(bucket.substring(bucket.indexOf("|m|") + 3));
    }

    public synchronized void reset() {
        windows.clear();
    }
}
