package com.mmrtr.lol.tools.riotmock.world;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.tools.riotmock.config.RiotMockProperties;
import com.mmrtr.lol.tools.riotmock.world.RiotRecords.AccountRecord;
import com.mmrtr.lol.tools.riotmock.world.RiotRecords.LeagueEntryRecord;
import com.mmrtr.lol.tools.riotmock.world.RiotRecords.SummonerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 저장소 없이 산술만으로 만드는 결정적 실험실 세계. 바뀌는 상태는 epoch 하나뿐이다.
 * 같은 (puuid, epoch, 질의)는 언제나 같은 결과를 낸다.
 */
public class LabWorld {

    private static final Pattern GAME_NAME = Pattern.compile("^lab(\\d{1,9})$");
    private static final String TAG_LINE = "LAB";
    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";
    private static final String[] TIERS = {"IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND"};
    private static final String[] DIVISIONS = {"IV", "III", "II", "I"};
    private static final int DEFAULT_COUNT = 20;
    private static final int MAX_COUNT = 100;

    private final RiotMockProperties.World cfg;
    private final MatchFactory matchFactory;
    private final int weightTotal;
    private volatile int epoch;

    public LabWorld(RiotMockProperties.World cfg) {
        this.cfg = cfg;
        this.matchFactory = new MatchFactory(cfg.gameVersion());
        this.weightTotal = cfg.newGameWeights().stream().mapToInt(Integer::intValue).sum();
    }

    public int epoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch 는 0 이상이어야 한다: " + epoch);
        }
        this.epoch = epoch;
    }

    public Optional<AccountRecord> accountByRiotId(String gameName, String tagLine) {
        if (gameName == null || tagLine == null || !TAG_LINE.equalsIgnoreCase(tagLine)) {
            return Optional.empty();
        }
        Matcher m = GAME_NAME.matcher(gameName.toLowerCase());
        if (!m.matches()) {
            return Optional.empty();
        }
        int n = Integer.parseInt(m.group(1));
        if (!isPlayer(n) || !String.valueOf(n).equals(m.group(1))) {
            return Optional.empty();
        }
        return Optional.of(new AccountRecord(LabIds.puuidOf(n), "lab" + n, TAG_LINE));
    }

    public Optional<AccountRecord> accountByPuuid(String puuid) {
        return playerOf(puuid).stream().mapToObj(n -> new AccountRecord(puuid, "lab" + n, TAG_LINE)).findFirst();
    }

    public Optional<SummonerRecord> summonerByPuuid(String puuid) {
        return playerOf(puuid).stream().mapToObj(n -> new SummonerRecord(
                puuid, 1 + n % 30, history(n, epoch).get(0).endMillis(), 30 + n % 470)).findFirst();
    }

    public Optional<List<LeagueEntryRecord>> leagueEntriesByPuuid(String puuid) {
        return playerOf(puuid).stream().mapToObj(n -> List.of(new LeagueEntryRecord(
                "lab-league-" + (n % 7), puuid, SOLO_QUEUE, TIERS[n % TIERS.length],
                DIVISIONS[n / TIERS.length % DIVISIONS.length],
                (int) ((LabIds.mix64(n) >>> 1) % 100), 20 + n % 180, 15 + n % 170,
                n % 11 == 0, n % 7 == 0, n % 13 == 0, false))).findFirst();
    }

    public Optional<List<String>> matchIds(String puuid, Long startTimeSec, Integer start, Integer count) {
        OptionalInt player = playerOf(puuid);
        if (player.isEmpty()) {
            return Optional.empty();
        }
        long minStart = startTimeSec == null ? Long.MIN_VALUE : startTimeSec * 1000;
        int skip = start == null ? 0 : Math.max(0, start);
        int limit = count == null ? DEFAULT_COUNT : Math.max(0, Math.min(MAX_COUNT, count));
        List<String> ids = history(player.getAsInt(), epoch).stream()
                .filter(ref -> ref.startMillis() >= minStart)
                .skip(skip)
                .limit(limit)
                .map(MatchRef::matchId)
                .toList();
        return Optional.of(ids);
    }

    public Optional<MatchDto> match(String matchId) {
        return LabIds.parseMatchId(matchId).filter(this::exists)
                .map(ref -> matchFactory.match(ref, participantsOf(ref)));
    }

    public Optional<TimelineDto> timeline(String matchId) {
        return LabIds.parseMatchId(matchId).filter(this::exists)
                .map(ref -> matchFactory.timeline(ref, participantsOf(ref)));
    }

    /** epoch e 에서 소환사 n 에게 새로 생기는 게임 수. */
    public int newGames(int n, int e) {
        if (e <= 0 || n <= cfg.firstVisitMax() || !isPlayer(n)) {
            return 0;
        }
        long h = LabIds.mix64(n * 1_000_003L + e);
        long u = (h >>> 1) % weightTotal;
        long cumulative = 0;
        for (int k = 0; k < cfg.newGameWeights().size(); k++) {
            cumulative += cfg.newGameWeights().get(k);
            if (u < cumulative) {
                return k;
            }
        }
        return 0;
    }

    private boolean isPlayer(int n) {
        return n >= 1 && n <= cfg.players();
    }

    private OptionalInt playerOf(String puuid) {
        OptionalInt n = LabIds.indexOf(puuid);
        return n.isPresent() && isPlayer(n.getAsInt()) ? n : OptionalInt.empty();
    }

    /** 소환사 n 의 기록을 최신순으로. epoch 게임(epoch 내림차순, j 내림차순) 뒤에 기본 기록 i=0..base-1 이 온다. */
    private List<MatchRef> history(int n, int atEpoch) {
        List<MatchRef> refs = new ArrayList<>();
        for (int e = atEpoch; e >= 1; e--) {
            for (int j = newGames(n, e) - 1; j >= 0; j--) {
                refs.add(LabIds.epochRef(e, n, j));
            }
        }
        for (int i = 0; i < cfg.baseHistory(); i++) {
            refs.add(i % 2 == 0 ? LabIds.sharedRef((n - 1) / 10, i / 2) : LabIds.personalRef(n, i / 2));
        }
        return refs;
    }

    private boolean exists(MatchRef ref) {
        return switch (ref.kind()) {
            case SHARED -> ref.owner() >= 0 && ref.owner() * 10 + 1 <= cfg.players() && 2 * ref.index() < cfg.baseHistory();
            case PERSONAL -> isPlayer(ref.owner()) && 2 * ref.index() + 1 < cfg.baseHistory();
            case EPOCH -> ref.epoch() >= 1 && ref.epoch() <= epoch && isPlayer(ref.owner())
                    && ref.index() < newGames(ref.owner(), ref.epoch());
        };
    }

    private List<String> participantsOf(MatchRef ref) {
        List<String> puuids = new ArrayList<>(10);
        if (ref.kind() == MatchRef.Kind.SHARED) {
            for (int n = ref.owner() * 10 + 1; n <= ref.owner() * 10 + 10; n++) {
                puuids.add(LabIds.puuidOf(n));
            }
            return puuids;
        }
        int ownerSlot = (ref.owner() + ref.index()) % 10;
        for (int slot = 0; slot < 10; slot++) {
            puuids.add(slot == ownerSlot ? LabIds.puuidOf(ref.owner()) : LabIds.npcPuuidOf(ref.owner() * 10L + slot));
        }
        return puuids;
    }
}
