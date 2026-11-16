package com.mmrtr.lol.tools.riotmock.world;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/** 실험실 소환사·매치 ID 의 생성 규칙과 역변환. 모두 산술이라 저장소가 필요 없다. */
public final class LabIds {

    public static final long T0 = Instant.parse("2026-06-01T00:00:00Z").toEpochMilli();
    public static final long GAME_MILLIS = 30L * 60_000;
    public static final long STEP_MILLIS = 45L * 60_000;
    public static final long DAY_MILLIS = 24L * 3_600_000;

    private static final String LAB_PREFIX = "lab";
    private static final String NPC_PREFIX = "npc";
    private static final String MATCH_PREFIX = "KR_";
    private static final long PERSONAL_BASE = 7_000_000_000L;
    private static final long SHARED_BASE = 7_900_000_000L;
    private static final long EPOCH_BASE = 8_000_000_000L;
    private static final int PUUID_LENGTH = 78;

    private LabIds() {
    }

    public static String puuidOf(int n) {
        return LAB_PREFIX + "%075d".formatted(n);
    }

    public static OptionalInt indexOf(String puuid) {
        if (puuid == null || puuid.length() != PUUID_LENGTH || !puuid.startsWith(LAB_PREFIX)) {
            return OptionalInt.empty();
        }
        String digits = puuid.substring(LAB_PREFIX.length());
        if (!digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalInt.empty();
        }
        String trimmed = numberPartOf(puuid);
        if (trimmed.length() > 9) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(trimmed));
    }

    public static String npcPuuidOf(long k) {
        return NPC_PREFIX + "%075d".formatted(k);
    }

    /** puuid 의 3글자 접두어 뒤 번호(앞자리 0 제거). 매치 참가자 표시 이름을 만들 때 쓴다. */
    static String numberPartOf(String puuid) {
        return puuid.substring(LAB_PREFIX.length()).replaceFirst("^0+(?=.)", "");
    }

    public static String personalMatchId(int n, int k) {
        return MATCH_PREFIX + (PERSONAL_BASE + n * 100L + k);
    }

    public static String sharedMatchId(int g, int k) {
        return MATCH_PREFIX + (SHARED_BASE + g * 100L + k);
    }

    public static String epochMatchId(int e, int n, int j) {
        return MATCH_PREFIX + (EPOCH_BASE + e * 100_000L + n * 10L + j);
    }

    public static MatchRef personalRef(int n, int k) {
        return new MatchRef(MatchRef.Kind.PERSONAL, n, 0, k, T0 - (2L * k + 1) * STEP_MILLIS);
    }

    public static MatchRef sharedRef(int g, int k) {
        return new MatchRef(MatchRef.Kind.SHARED, g, 0, k, T0 - 2L * k * STEP_MILLIS);
    }

    public static MatchRef epochRef(int e, int n, int j) {
        return new MatchRef(MatchRef.Kind.EPOCH, n, e, j, T0 + e * DAY_MILLIS + j * STEP_MILLIS);
    }

    public static Optional<MatchRef> parseMatchId(String matchId) {
        if (matchId == null || !matchId.startsWith(MATCH_PREFIX)) {
            return Optional.empty();
        }
        String digits = matchId.substring(MATCH_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 12 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        long v = Long.parseLong(digits);
        if (v >= EPOCH_BASE) {
            long x = v - EPOCH_BASE;
            return Optional.of(epochRef((int) (x / 100_000), (int) (x % 100_000 / 10), (int) (x % 10)));
        }
        if (v >= SHARED_BASE) {
            long x = v - SHARED_BASE;
            return Optional.of(sharedRef((int) (x / 100), (int) (x % 100)));
        }
        if (v >= PERSONAL_BASE) {
            long x = v - PERSONAL_BASE;
            return Optional.of(personalRef((int) (x / 100), (int) (x % 100)));
        }
        return Optional.empty();
    }

    /** SplitMix64 마무리 함수. 같은 입력은 언제나 같은 값을 낸다. */
    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
