package com.mmrtr.lol.tools.riotmock.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.tools.riotmock.config.RiotMockProperties;
import com.mmrtr.lol.tools.riotmock.world.RiotRecords.LeagueEntryRecord;
import com.mmrtr.lol.tools.riotmock.world.RiotRecords.SummonerRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LabWorldTest {

    private static final Set<String> TIERS =
            Set.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND");
    private static final long JAN_2026_SEC = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond();

    private final LabWorld world = new LabWorld(
            new RiotMockProperties.World(4000, 2500, 60, List.of(30, 40, 20, 10), "16.11.712.4021"));

    @Test
    @DisplayName("첫 방문 소환사의 첫 페이지는 최신 20개이고 시각이 엄격히 감소한다")
    void matchIds_첫방문은_최신20개() {
        // given
        String puuid = LabIds.puuidOf(1);

        // when
        List<String> ids = world.matchIds(puuid, JAN_2026_SEC, 0, 20).orElseThrow();

        // then
        assertThat(ids).hasSize(20);
        assertThat(ids.get(0)).isEqualTo(LabIds.sharedMatchId(0, 0));
        List<Long> starts = ids.stream().map(id -> LabIds.parseMatchId(id).orElseThrow().startMillis()).toList();
        for (int i = 1; i < starts.size(); i++) {
            assertThat(starts.get(i)).isLessThan(starts.get(i - 1));
        }
    }

    @Test
    @DisplayName("start 가 기록 수를 넘으면 빈 목록")
    void matchIds_start가_기록을_넘으면_빈목록() {
        // when
        List<String> ids = world.matchIds(LabIds.puuidOf(1), JAN_2026_SEC, 60, 20).orElseThrow();

        // then
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("count 는 100 으로 잘리고 기록은 60개다")
    void matchIds_count는_100으로_자른다() {
        // when
        List<String> ids = world.matchIds(LabIds.puuidOf(1), null, 0, 500).orElseThrow();

        // then
        assertThat(ids).hasSize(60).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("count 와 start 의 기본값은 20 과 0 이다")
    void matchIds_기본값() {
        // when
        List<String> ids = world.matchIds(LabIds.puuidOf(1), null, null, null).orElseThrow();

        // then
        assertThat(ids).hasSize(20);
    }

    @Test
    @DisplayName("마지막 게임 끝 시각(revisionDate) 이후로 요청하면 새 게임이 없다")
    void matchIds_마지막게임_끝시각_이후는_없다() {
        // given
        String puuid = LabIds.puuidOf(1);
        SummonerRecord summoner = world.summonerByPuuid(puuid).orElseThrow();

        // when
        List<String> ids = world.matchIds(puuid, summoner.revisionDate() / 1000, 0, 20).orElseThrow();

        // then
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("모르는 puuid 는 빈 값")
    void matchIds_모르는puuid는_빈값() {
        assertThat(world.matchIds("abc", null, 0, 20)).isEmpty();
        assertThat(world.matchIds(LabIds.puuidOf(4001), null, 0, 20)).isEmpty();
        assertThat(world.matchIds(LabIds.npcPuuidOf(1), null, 0, 20)).isEmpty();
    }

    @Test
    @DisplayName("epoch 0 과 첫 방문 풀은 K=0")
    void newGames_epoch0과_첫방문은_0() {
        for (int n = 1; n <= 4000; n++) {
            assertThat(world.newGames(n, 0)).isZero();
        }
        for (int n = 1; n <= 2500; n++) {
            assertThat(world.newGames(n, 3)).isZero();
        }
    }

    @Test
    @DisplayName("재방문 풀의 K 분포가 가중치 30/40/20/10 을 따른다")
    void newGames_분포가_가중치를_따른다() {
        // given
        int[] counts = new int[4];

        // when
        for (int n = 2501; n <= 4000; n++) {
            counts[world.newGames(n, 1)]++;
        }

        // then
        assertThat(counts[0] / 1500.0).isBetween(0.25, 0.35);
        assertThat(counts[1] / 1500.0).isBetween(0.35, 0.45);
        assertThat(counts[2] / 1500.0).isBetween(0.15, 0.25);
        assertThat(counts[3] / 1500.0).isBetween(0.05, 0.15);
    }

    @Test
    @DisplayName("같은 epoch 에서는 같은 목록, epoch 가 오르면 새 게임만 앞에 붙는다")
    void matchIds_같은epoch는_같은목록() {
        // given
        String puuid = LabIds.puuidOf(2600);
        world.setEpoch(1);

        // when
        List<String> first = world.matchIds(puuid, null, 0, 100).orElseThrow();
        List<String> second = world.matchIds(puuid, null, 0, 100).orElseThrow();
        world.setEpoch(2);
        List<String> next = world.matchIds(puuid, null, 0, 100).orElseThrow();

        // then
        assertThat(second).isEqualTo(first);
        assertThat(first).hasSize(60 + world.newGames(2600, 1));
        assertThat(next).hasSize(first.size() + world.newGames(2600, 2));
        assertThat(next).endsWith(first.toArray(String[]::new));
    }

    @Test
    @DisplayName("revisionDate 는 가장 최근 게임의 끝 시각")
    void summoner_revisionDate는_가장최근게임의_끝() {
        // given
        world.setEpoch(1);
        int withGames = firstN(1);
        int withoutGames = firstN(0);

        // when
        long afterNew = world.summonerByPuuid(LabIds.puuidOf(withGames)).orElseThrow().revisionDate();
        long noNew = world.summonerByPuuid(LabIds.puuidOf(withoutGames)).orElseThrow().revisionDate();

        // then
        int k = world.newGames(withGames, 1);
        assertThat(afterNew).isEqualTo(LabIds.epochRef(1, withGames, k - 1).startMillis() + 30 * 60_000L);
        assertThat(noNew).isEqualTo(LabIds.sharedRef((withoutGames - 1) / 10, 0).endMillis());
    }

    @Test
    @DisplayName("공유 게임의 참가자는 그룹 puuid 10개와 같다")
    void match_공유게임은_같은그룹_10명() {
        // given
        String matchId = LabIds.sharedMatchId(1, 2);
        List<String> group = new ArrayList<>();
        for (int n = 11; n <= 20; n++) {
            group.add(LabIds.puuidOf(n));
        }

        // when
        MatchDto match = world.match(matchId).orElseThrow();

        // then
        assertThat(match.getMetadata().getParticipants()).containsExactlyElementsOf(group);
        assertThat(world.timeline(matchId)).isPresent();
    }

    @Test
    @DisplayName("개인 게임은 주인 1명과 NPC 9명이다")
    void match_개인게임은_주인과_NPC() {
        // when
        MatchDto match = world.match(LabIds.personalMatchId(7, 3)).orElseThrow();

        // then
        List<String> puuids = match.getMetadata().getParticipants();
        assertThat(puuids).hasSize(10).contains(LabIds.puuidOf(7)).doesNotHaveDuplicates();
        assertThat(puuids.stream().filter(p -> p.startsWith("npc"))).hasSize(9);
    }

    @Test
    @DisplayName("모르는 매치 ID 는 빈 값")
    void match_모르는ID는_빈값() {
        assertThat(world.match("KR_1")).isEmpty();
        assertThat(world.match("garbage")).isEmpty();
        assertThat(world.match(LabIds.personalMatchId(4001, 0))).isEmpty();
        assertThat(world.match(LabIds.personalMatchId(1, 30))).isEmpty();
        assertThat(world.match(LabIds.epochMatchId(1, 2600, 0))).isEmpty();
        assertThat(world.timeline("KR_1")).isEmpty();
    }

    @Test
    @DisplayName("아직 오지 않은 epoch 의 게임은 없고 올라가면 생긴다")
    void match_epoch의_새게임은_epoch가_올라가야_생긴다() {
        // given
        int n = firstN(1);
        String id = LabIds.epochMatchId(1, n, 0);

        // when
        boolean before = world.match(id).isPresent();
        world.setEpoch(1);
        boolean after = world.match(id).isPresent();

        // then
        assertThat(before).isFalse();
        assertThat(after).isTrue();
    }

    @Test
    @DisplayName("범위 밖 번호와 다른 태그는 빈 값")
    void accountByRiotId_범위밖과_다른태그는_빈값() {
        assertThat(world.accountByRiotId("lab4001", "LAB")).isEmpty();
        assertThat(world.accountByRiotId("lab0", "LAB")).isEmpty();
        assertThat(world.accountByRiotId("lab1", "KR1")).isEmpty();
        assertThat(world.accountByRiotId("lab01", "LAB")).isEmpty();
        assertThat(world.accountByRiotId("other1", "LAB")).isEmpty();
        assertThat(world.accountByRiotId("lab4000", "LAB").orElseThrow().puuid()).isEqualTo(LabIds.puuidOf(4000));
        assertThat(world.accountByPuuid(LabIds.puuidOf(12)).orElseThrow().gameName()).isEqualTo("lab12");
        assertThat(world.accountByPuuid(LabIds.puuidOf(4001))).isEmpty();
    }

    @Test
    @DisplayName("소환사와 리그 항목은 앱이 받아들이는 값으로 여러 티어에 퍼진다")
    void leagueEntries_여러티어에_고르게() {
        // given
        Set<String> seen = new java.util.HashSet<>();

        // when
        for (int n = 1; n <= 70; n++) {
            LeagueEntryRecord entry = world.leagueEntriesByPuuid(LabIds.puuidOf(n)).orElseThrow().get(0);
            seen.add(entry.tier());
            assertThat(entry.queueType()).isEqualTo("RANKED_SOLO_5x5");
            assertThat(entry.puuid()).isEqualTo(LabIds.puuidOf(n));
            assertThat(entry.rank()).isIn("I", "II", "III", "IV");
            assertThat(entry.leaguePoints()).isBetween(0, 99);
            assertThat(TIERS).contains(entry.tier());
        }

        // then
        assertThat(seen).isEqualTo(TIERS);
        assertThat(world.leagueEntriesByPuuid("abc")).isEmpty();
        assertThat(world.summonerByPuuid(LabIds.puuidOf(1)).orElseThrow().summonerLevel()).isPositive();
    }

    /** epoch 1 에서 K 가 원하는 값(0 이면 0, 1 이면 1 이상)인 첫 재방문 소환사 번호. */
    private int firstN(int wantAtLeast) {
        for (int n = 2501; n <= 4000; n++) {
            int k = world.newGames(n, 1);
            if (wantAtLeast == 0 ? k == 0 : k >= 1) {
                return n;
            }
        }
        throw new IllegalStateException("조건에 맞는 소환사가 없다");
    }
}
