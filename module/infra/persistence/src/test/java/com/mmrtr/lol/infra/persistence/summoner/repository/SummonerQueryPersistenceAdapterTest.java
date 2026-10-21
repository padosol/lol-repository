package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueRecord;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 마이그레이션(Flyway) 스키마에서 공개 소환사 조회 — search_name 일치·접두사, 랭크 소환사 필터, 리그·이력 정렬. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, SummonerQueryPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SummonerQueryPersistenceAdapterTest {

    @Autowired
    private SummonerQueryPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // given
        for (String table : List.of("league_summoner_history", "league_summoner", "summoner")) {
            entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        summoner("faker", "KR", "hide on bush", "KR1");
        summoner("faker-old", "KR", "hide on bush", "KR2");
        summoner("faker-na", "NA1", "hide on bush", "KR1");
        summoner("unranked", "KR", "hide on bushes", "KR1");
        summoner("percent", "KR", "100%", "KR1");
        summoner("hundred", "KR", "1000", "KR1");
        league(1L, "faker", "RANKED_FLEX_SR", "PLATINUM", "IV", 10);
        league(2L, "faker", "RANKED_SOLO_5x5", "CHALLENGER", "I", 1500);
        league(3L, "faker-old", "RANKED_SOLO_5x5", "GOLD", "II", 45);
        league(4L, "faker-na", "RANKED_SOLO_5x5", "GOLD", "I", 1);
        league(5L, "percent", "RANKED_SOLO_5x5", "SILVER", "I", 1);
        league(6L, "hundred", "RANKED_SOLO_5x5", "SILVER", "II", 2);
        history(2L, "faker", "RANKED_SOLO_5x5", 1400, LocalDateTime.of(2026, 3, 1, 0, 0));
        history(2L, "faker", "RANKED_SOLO_5x5", 1450, LocalDateTime.of(2026, 3, 2, 0, 0));
        history(1L, "faker", "RANKED_FLEX_SR", 5, LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    @DisplayName("puuid 로 저장된 소환사를 찾는다")
    @Test
    void findSummoner_byPuuid_returnsStoredSummoner() {
        // when & then
        assertThat(adapter.findSummoner("faker")).get()
                .extracting(summoner -> summoner.getGameIdentity().tagLine()).isEqualTo("KR1");
        assertThat(adapter.findSummoner("ghost")).isEmpty();
    }

    @DisplayName("search_name 일치는 플랫폼 안에서만, 접두사는 태그가 다른 행까지 찾는다")
    @Test
    void findSummonersBySearchName_exactAndPrefix_staysInPlatform() {
        // when & then
        assertThat(adapter.findSummonersBySearchName("KR", "hideonbush#kr1", 2))
                .extracting(Summoner::getPuuid).containsExactly("faker");
        assertThat(adapter.findSummonersBySearchNamePrefix("KR", "hideonbush#", 5))
                .extracting(Summoner::getPuuid).containsExactlyInAnyOrder("faker", "faker-old");
        assertThat(adapter.findSummonersBySearchNamePrefix("KR", "hideonbush#", 1)).hasSize(1);
    }

    @DisplayName("자동완성은 랭크 기록이 있는 소환사만 search_name 순으로, 리그 정보를 채워 돌려준다")
    @Test
    void findRankedSummonersBySearchNamePrefix_prefix_returnsRankedOnlyInOrder() {
        // when
        List<Summoner> result = adapter.findRankedSummonersBySearchNamePrefix("KR", "hideonbush", 20);

        // then
        assertThat(result).extracting(Summoner::getPuuid).containsExactly("faker", "faker-old");
        assertThat(result.get(0).getLeagueInfos()).extracting(LeagueInfo::getQueueType)
                .containsExactlyInAnyOrder("RANKED_SOLO_5x5", "RANKED_FLEX_SR");
        assertThat(adapter.findRankedSummonersBySearchNamePrefix("KR", "hideonbush", 1))
                .extracting(Summoner::getPuuid).containsExactly("faker");
    }

    @DisplayName("검색어의 % · _ 는 LIKE 와일드카드가 아니라 글자로 찾는다")
    @Test
    void findRankedSummonersBySearchNamePrefix_wildcards_matchLiterally() {
        // when & then
        assertThat(adapter.findRankedSummonersBySearchNamePrefix("KR", "100%", 20))
                .extracting(Summoner::getPuuid).containsExactly("percent");
        assertThat(adapter.findRankedSummonersBySearchNamePrefix("KR", "10_0", 20)).isEmpty();
        assertThat(adapter.findSummonersBySearchNamePrefix("KR", "10_0", 5)).isEmpty();
    }

    @DisplayName("현재 랭크는 저장 순서, 이력은 최신순")
    @Test
    void findLeaguesAndHistories_byPuuid_returnsOrderedRecords() {
        // when & then
        assertThat(adapter.findLeagues("faker")).containsExactly(
                new LeagueRecord("RANKED_FLEX_SR", 10, 0, 0, "PLATINUM", "IV"),
                new LeagueRecord("RANKED_SOLO_5x5", 1500, 0, 0, "CHALLENGER", "I"));
        assertThat(adapter.findLeagueHistories("faker")).extracting(LeagueRecord::leaguePoints)
                .containsExactly(1450, 1400, 5);
        assertThat(adapter.findLeagues("ghost")).isEmpty();
        assertThat(adapter.findLeagueHistories("ghost")).isEmpty();
    }

    private void summoner(String puuid, String platformId, String gameName, String tagLine) {
        insert("summoner", Map.of("puuid", puuid, "platform_id", platformId, "game_name", gameName,
                "tag_line", tagLine, "search_name", (gameName.replace(" ", "") + "#" + tagLine).toLowerCase()));
    }

    private void league(long id, String puuid, String queue, String tier, String rank, int leaguePoints) {
        insert("league_summoner", Map.of("league_summoner_id", id, "puuid", puuid, "queue", queue, "tier", tier,
                "rank", rank, "league_points", leaguePoints));
    }

    private void history(long leagueSummonerId, String puuid, String queue, int leaguePoints,
                         LocalDateTime createdAt) {
        insert("league_summoner_history", Map.of("league_summoner_id", leagueSummonerId, "puuid", puuid,
                "queue", queue, "tier", "CHALLENGER", "rank", "I", "league_points", leaguePoints,
                "created_at", createdAt));
    }

    /** 기본값 없는 NOT NULL 컬럼은 타입별 0 값으로 채우고, 테스트에 필요한 컬럼만 지정한다. */
    @SuppressWarnings("unchecked")
    private void insert(String table, Map<String, Object> values) {
        List<Object[]> requiredColumns = entityManager.createNativeQuery("""
                        SELECT column_name, data_type FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = :table
                          AND is_nullable = 'NO' AND column_default IS NULL AND is_identity = 'NO'
                        """)
                .setParameter("table", table)
                .getResultList();
        Map<String, Object> row = new LinkedHashMap<>();
        for (Object[] column : requiredColumns) {
            row.put((String) column[0], zeroOf((String) column[1]));
        }
        row.putAll(values);

        List<String> placeholders = new ArrayList<>();
        for (int i = 1; i <= row.size(); i++) {
            placeholders.add("?" + i);
        }
        Query query = entityManager.createNativeQuery("INSERT INTO " + table + " (" + String.join(", ", row.keySet())
                + ") VALUES (" + String.join(", ", placeholders) + ")");
        int position = 1;
        for (Object value : row.values()) {
            query.setParameter(position++, value);
        }
        query.executeUpdate();
    }

    private Object zeroOf(String dataType) {
        return switch (dataType) {
            case "boolean" -> false;
            case "smallint", "integer" -> 0;
            case "bigint" -> 0L;
            case "real", "double precision", "numeric" -> 0.0;
            default -> "";
        };
    }
}
