package com.mmrtr.lol.infra.persistence.champion_stat.service;

import com.mmrtr.lol.common.type.BuildType;
import com.mmrtr.lol.common.type.ItemCategory;
import com.mmrtr.lol.common.type.TierGroup;
import com.mmrtr.lol.domain.champion_stat.domain.*;
import com.mmrtr.lol.domain.champion_stat.repository.ChampionStatRepositoryPort;
import com.mmrtr.lol.domain.champion_stat.service.usecase.TriggerChampionStatAggregationUseCase;
import com.mmrtr.lol.infra.persistence.champion_stat.entity.ItemMetadataEntity;
import com.mmrtr.lol.infra.persistence.champion_stat.repository.ItemMetadataJpaRepository;
import com.mmrtr.lol.infra.redis.service.ChampionStatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionStatAggregationService implements TriggerChampionStatAggregationUseCase {

    private final ChampionStatRepositoryPort championStatRepositoryPort;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ItemMetadataJpaRepository itemMetadataJpaRepository;
    private final ChampionStatCacheService championStatCacheService;

    @Override
    @Transactional
    public void execute(int season, int queueId) {
        log.info("챔피언 통계 집계 시작 - season: {}, queueId: {}", season, queueId);

        // 1. 기존 집계 데이터 삭제
        championStatRepositoryPort.deleteAllBySeasonAndQueueId(season, queueId);

        // 2. 사용 가능한 차원 조합 조회
        List<Map<String, Object>> dimensions = queryDistinctDimensions(season, queueId);
        log.info("집계 대상 차원 조합 수: {}", dimensions.size());

        // 3. 아이템 메타데이터 로드
        Map<Integer, ItemMetadataEntity> itemMetadataMap = itemMetadataJpaRepository.findAll().stream()
                .collect(Collectors.toMap(ItemMetadataEntity::getItemId, e -> e, (a, b) -> a));

        // 4. 차원 조합별 루프
        for (Map<String, Object> dim : dimensions) {
            String platformId = (String) dim.get("platform_id");
            String gameVersion = (String) dim.get("game_version");

            for (TierGroup tierGroup : TierGroup.values()) {
                try {
                    aggregateForDimension(season, queueId, tierGroup, platformId, gameVersion, itemMetadataMap);
                } catch (Exception e) {
                    log.error("집계 실패 - tierGroup: {}, platform: {}, version: {}", tierGroup, platformId, gameVersion, e);
                }
            }
        }

        // 5. 캐시 무효화
        championStatCacheService.evictAll();

        log.info("챔피언 통계 집계 완료 - season: {}, queueId: {}", season, queueId);
    }

    private List<Map<String, Object>> queryDistinctDimensions(int season, int queueId) {
        String sql = "SELECT DISTINCT m.platform_id, SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') AS game_version " +
                "FROM match m WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') IS NOT NULL";

        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource()
                .addValue("season", season)
                .addValue("queueId", queueId));
    }

    private void aggregateForDimension(int season, int queueId, TierGroup tierGroup,
                                       String platformId, String gameVersion,
                                       Map<Integer, ItemMetadataEntity> itemMetadataMap) {
        Set<String> tiers = tierGroup.getIncludedTiers();
        String tierGroupName = tierGroup.name();

        // Summary 집계
        aggregateSummary(season, queueId, tierGroupName, tiers, platformId, gameVersion);

        // Rune 집계
        aggregateRune(season, queueId, tierGroupName, tiers, platformId, gameVersion);

        // Spell 집계
        aggregateSpell(season, queueId, tierGroupName, tiers, platformId, gameVersion);

        // Skill 집계
        aggregateSkill(season, queueId, tierGroupName, tiers, platformId, gameVersion);

        // Item 집계
        aggregateItem(season, queueId, tierGroupName, tiers, platformId, gameVersion, itemMetadataMap);

        // Matchup 집계
        aggregateMatchup(season, queueId, tierGroupName, tiers, platformId, gameVersion);
    }

    private MapSqlParameterSource createBaseParams(int season, int queueId, Set<String> tiers, String platformId, String gameVersion) {
        return new MapSqlParameterSource()
                .addValue("season", season)
                .addValue("queueId", queueId)
                .addValue("tiers", tiers)
                .addValue("platformId", platformId)
                .addValue("gameVersion", gameVersion);
    }

    // === Summary 집계 ===
    private void aggregateSummary(int season, int queueId, String tierGroupName,
                                  Set<String> tiers, String platformId, String gameVersion) {
        String sql = "SELECT ms.champion_id, ms.team_position, " +
                "COUNT(*) AS total_games, " +
                "SUM(CASE WHEN ms.win THEN 1 ELSE 0 END) AS wins, " +
                "AVG(ms.kills) AS avg_kills, " +
                "AVG(ms.deaths) AS avg_deaths, " +
                "AVG(ms.assists) AS avg_assists, " +
                "AVG(ms.total_minions_killed + ms.neutral_minions_killed) AS avg_cs, " +
                "AVG(ms.gold_earned) AS avg_gold " +
                "FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) " +
                "AND ms.team_position != '' " +
                "GROUP BY ms.champion_id, ms.team_position";

        MapSqlParameterSource params = createBaseParams(season, queueId, tiers, platformId, gameVersion);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

        // 밴 수 집계 (서브쿼리로 unnest 후 GROUP BY)
        String banSql = "SELECT champion_id, COUNT(*) AS ban_count FROM (" +
                "SELECT unnest(ARRAY[mt.champion1id, mt.champion2id, mt.champion3id, mt.champion4id, mt.champion5id]) AS champion_id " +
                "FROM match_team mt " +
                "JOIN match m ON mt.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion" +
                ") AS ban_list WHERE champion_id > 0 GROUP BY champion_id";

        Map<Integer, Long> banCounts;
        try {
            banCounts = jdbcTemplate.queryForList(banSql, params).stream()
                    .collect(Collectors.toMap(
                            r -> ((Number) r.get("champion_id")).intValue(),
                            r -> ((Number) r.get("ban_count")).longValue(),
                            Long::sum));
        } catch (Exception e) {
            log.error("밴 수 집계 실패, 0으로 대체 - platform: {}, version: {}", platformId, gameVersion, e);
            banCounts = Map.of();
        }

        // 총 매치 수 조회
        String totalMatchSql = "SELECT COUNT(DISTINCT m.match_id) AS total " +
                "FROM match m WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion";

        Long totalMatchesResult = jdbcTemplate.queryForObject(totalMatchSql, params, Long.class);
        long totalMatches = totalMatchesResult != null ? totalMatchesResult : 0L;

        Map<Integer, Long> finalBanCounts = banCounts;
        List<ChampionStatSummary> summaries = rows.stream()
                .map(row -> ChampionStatSummary.builder()
                        .championId(toInt(row.get("champion_id")))
                        .teamPosition((String) row.get("team_position"))
                        .season(season)
                        .tierGroup(tierGroupName)
                        .platformId(platformId)
                        .queueId(queueId)
                        .gameVersion(gameVersion)
                        .totalGames(toLong(row.get("total_games")))
                        .wins(toLong(row.get("wins")))
                        .totalBans(finalBanCounts.getOrDefault(toInt(row.get("champion_id")), 0L))
                        .totalMatchesInDimension(totalMatches)
                        .avgKills(toBigDecimal(row.get("avg_kills")))
                        .avgDeaths(toBigDecimal(row.get("avg_deaths")))
                        .avgAssists(toBigDecimal(row.get("avg_assists")))
                        .avgCs(toBigDecimal(row.get("avg_cs")))
                        .avgGold(toBigDecimal(row.get("avg_gold")))
                        .build())
                .toList();

        championStatRepositoryPort.bulkSaveSummaries(summaries);
    }

    // === Rune 집계 ===
    private void aggregateRune(int season, int queueId, String tierGroupName,
                               Set<String> tiers, String platformId, String gameVersion) {
        String sql = "SELECT ms.champion_id, ms.team_position, " +
                "ms.primary_rune_id, ms.primary_rune_ids, ms.secondary_rune_id, ms.secondary_rune_ids, " +
                "ms.offense, ms.flex, ms.defense, " +
                "COUNT(*) AS games, " +
                "SUM(CASE WHEN ms.win THEN 1 ELSE 0 END) AS wins " +
                "FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) " +
                "AND ms.team_position != '' " +
                "GROUP BY ms.champion_id, ms.team_position, " +
                "ms.primary_rune_id, ms.primary_rune_ids, ms.secondary_rune_id, ms.secondary_rune_ids, " +
                "ms.offense, ms.flex, ms.defense";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                createBaseParams(season, queueId, tiers, platformId, gameVersion));

        List<ChampionRuneStat> runeStats = rows.stream()
                .map(row -> ChampionRuneStat.builder()
                        .championId(toInt(row.get("champion_id")))
                        .teamPosition((String) row.get("team_position"))
                        .season(season)
                        .tierGroup(tierGroupName)
                        .platformId(platformId)
                        .queueId(queueId)
                        .gameVersion(gameVersion)
                        .primaryRuneId(toInt(row.get("primary_rune_id")))
                        .primaryRuneIds((String) row.get("primary_rune_ids"))
                        .secondaryRuneId(toInt(row.get("secondary_rune_id")))
                        .secondaryRuneIds((String) row.get("secondary_rune_ids"))
                        .statOffense(toInt(row.get("offense")))
                        .statFlex(toInt(row.get("flex")))
                        .statDefense(toInt(row.get("defense")))
                        .games(toLong(row.get("games")))
                        .wins(toLong(row.get("wins")))
                        .build())
                .toList();

        championStatRepositoryPort.bulkSaveRuneStats(runeStats);
    }

    // === Spell 집계 ===
    private void aggregateSpell(int season, int queueId, String tierGroupName,
                                Set<String> tiers, String platformId, String gameVersion) {
        String sql = "SELECT ms.champion_id, ms.team_position, " +
                "LEAST(ms.summoner1_id, ms.summoner2_id) AS spell1_id, " +
                "GREATEST(ms.summoner1_id, ms.summoner2_id) AS spell2_id, " +
                "COUNT(*) AS games, " +
                "SUM(CASE WHEN ms.win THEN 1 ELSE 0 END) AS wins " +
                "FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) " +
                "AND ms.team_position != '' " +
                "GROUP BY ms.champion_id, ms.team_position, spell1_id, spell2_id";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                createBaseParams(season, queueId, tiers, platformId, gameVersion));

        List<ChampionSpellStat> spellStats = rows.stream()
                .map(row -> ChampionSpellStat.builder()
                        .championId(toInt(row.get("champion_id")))
                        .teamPosition((String) row.get("team_position"))
                        .season(season)
                        .tierGroup(tierGroupName)
                        .platformId(platformId)
                        .queueId(queueId)
                        .gameVersion(gameVersion)
                        .spell1Id(toInt(row.get("spell1_id")))
                        .spell2Id(toInt(row.get("spell2_id")))
                        .games(toLong(row.get("games")))
                        .wins(toLong(row.get("wins")))
                        .build())
                .toList();

        championStatRepositoryPort.bulkSaveSpellStats(spellStats);
    }

    // === Skill 집계 (챔피언별 배치 처리) ===
    private void aggregateSkill(int season, int queueId, String tierGroupName,
                                Set<String> tiers, String platformId, String gameVersion) {
        MapSqlParameterSource baseParams = createBaseParams(season, queueId, tiers, platformId, gameVersion);

        // 1. 해당 차원의 distinct champion_id 목록 조회
        String championSql = "SELECT DISTINCT ms.champion_id FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) AND ms.team_position != ''";

        List<Integer> championIds = jdbcTemplate.queryForList(championSql, baseParams, Integer.class);

        // 2. 챔피언별 skill_events 조회 후 처리
        String sql = "SELECT ms.champion_id, ms.team_position, ms.match_id, ms.participant_id, ms.win, " +
                "se.skill_slot, se.timestamp " +
                "FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "JOIN skill_events se ON se.match_id = ms.match_id AND se.participant_id = ms.participant_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) " +
                "AND ms.team_position != '' " +
                "AND ms.champion_id = :championId " +
                "AND se.level_up_type = 'NORMAL' " +
                "ORDER BY ms.match_id, ms.participant_id, se.timestamp";

        Map<String, SkillAggregation> skillAggMap = new HashMap<>();

        for (int champId : championIds) {
            MapSqlParameterSource params = createBaseParams(season, queueId, tiers, platformId, gameVersion)
                    .addValue("championId", champId);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

            Map<String, List<Map<String, Object>>> grouped = rows.stream()
                    .collect(Collectors.groupingBy(r ->
                            r.get("match_id") + ":" + r.get("participant_id"), LinkedHashMap::new, Collectors.toList()));

            for (List<Map<String, Object>> events : grouped.values()) {
                if (events.isEmpty()) continue;

                Map<String, Object> first = events.get(0);
                int championId = ((Number) first.get("champion_id")).intValue();
                String teamPosition = (String) first.get("team_position");
                boolean win = (Boolean) first.get("win");

                String skillOrder = events.stream()
                        .limit(18)
                        .map(e -> String.valueOf(((Number) e.get("skill_slot")).intValue()))
                        .collect(Collectors.joining(","));

                String skillPriority = calculateSkillPriority(events);

                String key = championId + ":" + teamPosition + ":" + skillOrder;
                skillAggMap.computeIfAbsent(key, k -> new SkillAggregation(championId, teamPosition, skillOrder, skillPriority));
                SkillAggregation agg = skillAggMap.get(key);
                agg.games++;
                if (win) agg.wins++;
            }
        }

        List<ChampionSkillStat> skillStats = skillAggMap.values().stream()
                .map(agg -> ChampionSkillStat.builder()
                        .championId(agg.championId)
                        .teamPosition(agg.teamPosition)
                        .season(season)
                        .tierGroup(tierGroupName)
                        .platformId(platformId)
                        .queueId(queueId)
                        .gameVersion(gameVersion)
                        .skillOrder(agg.skillOrder)
                        .skillPriority(agg.skillPriority)
                        .games(agg.games)
                        .wins(agg.wins)
                        .build())
                .toList();

        championStatRepositoryPort.bulkSaveSkillStats(skillStats);
    }

    private String calculateSkillPriority(List<Map<String, Object>> events) {
        // 레벨 1~9의 스킬 투자 순서를 기반으로 Q>W>E 우선순위 계산
        int[] counts = new int[5]; // index 1=Q, 2=W, 3=E, 4=R
        int limit = Math.min(events.size(), 9);
        for (int i = 0; i < limit; i++) {
            int slot = ((Number) events.get(i).get("skill_slot")).intValue();
            if (slot >= 1 && slot <= 4) {
                counts[slot]++;
            }
        }

        // Q, W, E만 비교 (R 제외)
        String[] skills = {"Q", "W", "E"};
        int[] skillCounts = {counts[1], counts[2], counts[3]};

        // 카운트 기준 정렬
        Integer[] indices = {0, 1, 2};
        Arrays.sort(indices, (a, b) -> skillCounts[b] - skillCounts[a]);

        return skills[indices[0]] + ">" + skills[indices[1]] + ">" + skills[indices[2]];
    }

    // === Item 집계 (챔피언별 배치 처리) ===
    private void aggregateItem(int season, int queueId, String tierGroupName,
                               Set<String> tiers, String platformId, String gameVersion,
                               Map<Integer, ItemMetadataEntity> itemMetadataMap) {
        MapSqlParameterSource baseParams = createBaseParams(season, queueId, tiers, platformId, gameVersion);

        // 1. 해당 차원의 distinct champion_id 목록 조회
        String championSql = "SELECT DISTINCT ms.champion_id FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) AND ms.team_position != ''";

        List<Integer> championIds = jdbcTemplate.queryForList(championSql, baseParams, Integer.class);

        // 2. 챔피언별 item_events 조회 (ITEM_PURCHASED만)
        String sql = "SELECT ms.champion_id, ms.team_position, ms.match_id, ms.participant_id, ms.win, " +
                "ie.item_id, ie.timestamp " +
                "FROM match_summoner ms " +
                "JOIN match m ON ms.match_id = m.match_id " +
                "JOIN item_events ie ON ie.match_id = ms.match_id AND ie.participant_id = ms.participant_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms.tier IN (:tiers) " +
                "AND ms.team_position != '' " +
                "AND ms.champion_id = :championId " +
                "AND ie.type = 'ITEM_PURCHASED' " +
                "ORDER BY ms.match_id, ms.participant_id, ie.timestamp";

        Map<String, ItemAggregation> itemAggMap = new HashMap<>();

        for (int champId : championIds) {
            MapSqlParameterSource params = createBaseParams(season, queueId, tiers, platformId, gameVersion)
                    .addValue("championId", champId);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

            Map<String, List<Map<String, Object>>> grouped = rows.stream()
                    .collect(Collectors.groupingBy(r ->
                            r.get("match_id") + ":" + r.get("participant_id"), LinkedHashMap::new, Collectors.toList()));

            for (List<Map<String, Object>> events : grouped.values()) {
                if (events.isEmpty()) continue;

                Map<String, Object> first = events.get(0);
                int championId = ((Number) first.get("champion_id")).intValue();
                String teamPosition = (String) first.get("team_position");
                boolean win = (Boolean) first.get("win");

                List<Integer> starterItems = new ArrayList<>();
                List<Integer> bootsItems = new ArrayList<>();
                List<Integer> coreItems = new ArrayList<>();

                for (Map<String, Object> event : events) {
                    int itemId = ((Number) event.get("item_id")).intValue();
                    ItemMetadataEntity meta = itemMetadataMap.get(itemId);
                    if (meta == null) continue;

                    String category = meta.getItemCategory();
                    if (ItemCategory.STARTER.name().equals(category) && starterItems.size() < 3) {
                        starterItems.add(itemId);
                    } else if (ItemCategory.BOOTS.name().equals(category) && bootsItems.isEmpty()) {
                        bootsItems.add(itemId);
                    } else if (ItemCategory.LEGENDARY.name().equals(category) && coreItems.size() < 3) {
                        coreItems.add(itemId);
                    }
                }

                // 스타터 빌드
                if (!starterItems.isEmpty()) {
                    Collections.sort(starterItems);
                    String itemIds = starterItems.stream().map(String::valueOf).collect(Collectors.joining(","));
                    String key = championId + ":" + teamPosition + ":" + BuildType.STARTER.name() + ":" + itemIds;
                    itemAggMap.computeIfAbsent(key, k -> new ItemAggregation(championId, teamPosition, BuildType.STARTER.name(), itemIds));
                    ItemAggregation agg = itemAggMap.get(key);
                    agg.games++;
                    if (win) agg.wins++;
                }

                // 부츠 빌드
                if (!bootsItems.isEmpty()) {
                    String itemIds = String.valueOf(bootsItems.get(0));
                    String key = championId + ":" + teamPosition + ":" + BuildType.BOOTS.name() + ":" + itemIds;
                    itemAggMap.computeIfAbsent(key, k -> new ItemAggregation(championId, teamPosition, BuildType.BOOTS.name(), itemIds));
                    ItemAggregation agg = itemAggMap.get(key);
                    agg.games++;
                    if (win) agg.wins++;
                }

                // 코어 빌드
                if (!coreItems.isEmpty()) {
                    Collections.sort(coreItems);
                    String itemIds = coreItems.stream().map(String::valueOf).collect(Collectors.joining(","));
                    String key = championId + ":" + teamPosition + ":" + BuildType.CORE.name() + ":" + itemIds;
                    itemAggMap.computeIfAbsent(key, k -> new ItemAggregation(championId, teamPosition, BuildType.CORE.name(), itemIds));
                    ItemAggregation agg = itemAggMap.get(key);
                    agg.games++;
                    if (win) agg.wins++;
                }
            }
        }

        List<ChampionItemStat> itemStats = itemAggMap.values().stream()
                .map(agg -> ChampionItemStat.builder()
                        .championId(agg.championId)
                        .teamPosition(agg.teamPosition)
                        .season(season)
                        .tierGroup(tierGroupName)
                        .platformId(platformId)
                        .queueId(queueId)
                        .gameVersion(gameVersion)
                        .buildType(agg.buildType)
                        .itemIds(agg.itemIds)
                        .games(agg.games)
                        .wins(agg.wins)
                        .build())
                .toList();

        championStatRepositoryPort.bulkSaveItemStats(itemStats);
    }

    // === Matchup 집계 ===
    private void aggregateMatchup(int season, int queueId, String tierGroupName,
                                  Set<String> tiers, String platformId, String gameVersion) {
        String sql = "SELECT ms1.champion_id, ms1.team_position, ms2.champion_id AS opponent_champion_id, " +
                "COUNT(*) AS games, " +
                "SUM(CASE WHEN ms1.win THEN 1 ELSE 0 END) AS wins, " +
                "AVG(ms1.kills) AS avg_kills, " +
                "AVG(ms1.deaths) AS avg_deaths, " +
                "AVG(ms1.assists) AS avg_assists, " +
                "AVG(ms1.gold_earned - ms2.gold_earned) AS avg_gold_diff " +
                "FROM match_summoner ms1 " +
                "JOIN match_summoner ms2 ON ms1.match_id = ms2.match_id " +
                "AND ms1.team_position = ms2.team_position " +
                "AND ms1.team_id != ms2.team_id " +
                "JOIN match m ON ms1.match_id = m.match_id " +
                "WHERE m.season = :season AND m.queue_id = :queueId " +
                "AND m.platform_id = :platformId " +
                "AND SUBSTRING(m.game_version FROM '^(\\d+\\.\\d+)') = :gameVersion " +
                "AND ms1.tier IN (:tiers) " +
                "AND ms1.team_position != '' " +
                "GROUP BY ms1.champion_id, ms1.team_position, ms2.champion_id";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                createBaseParams(season, queueId, tiers, platformId, gameVersion));

        List<ChampionMatchupStat> matchupStats = rows.stream()
                .map(row -> ChampionMatchupStat.builder()
                        .championId(toInt(row.get("champion_id")))
                        .teamPosition((String) row.get("team_position"))
                        .season(season)
                        .tierGroup(tierGroupName)
                        .platformId(platformId)
                        .queueId(queueId)
                        .gameVersion(gameVersion)
                        .opponentChampionId(toInt(row.get("opponent_champion_id")))
                        .games(toLong(row.get("games")))
                        .wins(toLong(row.get("wins")))
                        .avgKills(toBigDecimal(row.get("avg_kills")))
                        .avgDeaths(toBigDecimal(row.get("avg_deaths")))
                        .avgAssists(toBigDecimal(row.get("avg_assists")))
                        .avgGoldDiff(toBigDecimal(row.get("avg_gold_diff")))
                        .build())
                .toList();

        championStatRepositoryPort.bulkSaveMatchupStats(matchupStats);
    }

    private int toInt(Object value) {
        return value != null ? ((Number) value).intValue() : 0;
    }

    private long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : 0L;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    // 내부 집계 헬퍼 클래스
    private static class SkillAggregation {
        final int championId;
        final String teamPosition;
        final String skillOrder;
        final String skillPriority;
        long games;
        long wins;

        SkillAggregation(int championId, String teamPosition, String skillOrder, String skillPriority) {
            this.championId = championId;
            this.teamPosition = teamPosition;
            this.skillOrder = skillOrder;
            this.skillPriority = skillPriority;
        }
    }

    private static class ItemAggregation {
        final int championId;
        final String teamPosition;
        final String buildType;
        final String itemIds;
        long games;
        long wins;

        ItemAggregation(int championId, String teamPosition, String buildType, String itemIds) {
            this.championId = championId;
            this.teamPosition = teamPosition;
            this.buildType = buildType;
            this.itemIds = itemIds;
        }
    }
}
