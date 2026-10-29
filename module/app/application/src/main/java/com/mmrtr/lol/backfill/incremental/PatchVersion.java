package com.mmrtr.lol.backfill.incremental;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 패치 버전 "{major}.{minor}". 문자열이 아니라 숫자로 비교한다 (16.9 &lt; 16.21).
 * 앞자리 0 은 정규화된다 (16.01 → 16.1).
 */
public record PatchVersion(int major, int minor) implements Comparable<PatchVersion> {

    private static final Pattern FORMAT = Pattern.compile("\\d+\\.\\d+");
    private static final Comparator<PatchVersion> ORDER =
            Comparator.comparingInt(PatchVersion::major).thenComparingInt(PatchVersion::minor);

    public static PatchVersion parse(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("패치 버전 형식이 아닙니다: " + value);
        }
        int dot = value.indexOf('.');
        try {
            return new PatchVersion(Integer.parseInt(value.substring(0, dot)), Integer.parseInt(value.substring(dot + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("패치 버전 범위를 벗어났습니다: " + value, e);
        }
    }

    public String value() {
        return major + "." + minor;
    }

    public static String join(List<PatchVersion> patches) {
        return patches.stream().map(PatchVersion::value).collect(Collectors.joining(","));
    }

    public static List<PatchVersion> split(String joined) {
        if (joined == null || joined.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(PatchVersion::parse).toList();
    }

    @Override
    public int compareTo(PatchVersion other) {
        return ORDER.compare(this, other);
    }
}
