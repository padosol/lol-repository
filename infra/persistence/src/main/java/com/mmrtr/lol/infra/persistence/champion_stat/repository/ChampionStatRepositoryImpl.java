package com.mmrtr.lol.infra.persistence.champion_stat.repository;

import com.mmrtr.lol.domain.champion_stat.domain.*;
import com.mmrtr.lol.domain.champion_stat.repository.ChampionStatRepositoryPort;
import com.mmrtr.lol.infra.persistence.champion_stat.entity.*;
import com.mmrtr.lol.infra.persistence.champion_stat.entity.value.StatDimensionValue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChampionStatRepositoryImpl implements ChampionStatRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ChampionStatSummaryJpaRepository summaryJpaRepository;
    private final ChampionRuneStatJpaRepository runeStatJpaRepository;
    private final ChampionSpellStatJpaRepository spellStatJpaRepository;
    private final ChampionSkillStatJpaRepository skillStatJpaRepository;
    private final ChampionItemStatJpaRepository itemStatJpaRepository;
    private final ChampionMatchupStatJpaRepository matchupStatJpaRepository;

    @Override
    @Transactional
    public void deleteAllBySeasonAndQueueId(int season, int queueId) {
        summaryJpaRepository.deleteByDimensionSeasonAndDimensionQueueId(season, queueId);
        runeStatJpaRepository.deleteByDimensionSeasonAndDimensionQueueId(season, queueId);
        spellStatJpaRepository.deleteByDimensionSeasonAndDimensionQueueId(season, queueId);
        skillStatJpaRepository.deleteByDimensionSeasonAndDimensionQueueId(season, queueId);
        itemStatJpaRepository.deleteByDimensionSeasonAndDimensionQueueId(season, queueId);
        matchupStatJpaRepository.deleteByDimensionSeasonAndDimensionQueueId(season, queueId);
    }

    // === 벌크 저장 ===

    @Override
    public void bulkSaveSummaries(List<ChampionStatSummary> summaries) {
        if (summaries.isEmpty()) return;

        String sql = "INSERT INTO champion_stat_summary (" +
                "champion_id, team_position, season, tier_group, platform_id, queue_id, game_version, " +
                "total_games, wins, total_bans, total_matches_in_dimension, " +
                "avg_kills, avg_deaths, avg_assists, avg_cs, avg_gold" +
                ") VALUES (" +
                ":championId, :teamPosition, :season, :tierGroup, :platformId, :queueId, :gameVersion, " +
                ":totalGames, :wins, :totalBans, :totalMatchesInDimension, " +
                ":avgKills, :avgDeaths, :avgAssists, :avgCs, :avgGold)";

        SqlParameterSource[] params = summaries.stream()
                .map(s -> new MapSqlParameterSource()
                        .addValue("championId", s.getChampionId())
                        .addValue("teamPosition", s.getTeamPosition())
                        .addValue("season", s.getSeason())
                        .addValue("tierGroup", s.getTierGroup())
                        .addValue("platformId", s.getPlatformId())
                        .addValue("queueId", s.getQueueId())
                        .addValue("gameVersion", s.getGameVersion())
                        .addValue("totalGames", s.getTotalGames())
                        .addValue("wins", s.getWins())
                        .addValue("totalBans", s.getTotalBans())
                        .addValue("totalMatchesInDimension", s.getTotalMatchesInDimension())
                        .addValue("avgKills", s.getAvgKills())
                        .addValue("avgDeaths", s.getAvgDeaths())
                        .addValue("avgAssists", s.getAvgAssists())
                        .addValue("avgCs", s.getAvgCs())
                        .addValue("avgGold", s.getAvgGold()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public void bulkSaveRuneStats(List<ChampionRuneStat> runeStats) {
        if (runeStats.isEmpty()) return;

        String sql = "INSERT INTO champion_rune_stat (" +
                "champion_id, team_position, season, tier_group, platform_id, queue_id, game_version, " +
                "primary_rune_id, primary_rune_ids, secondary_rune_id, secondary_rune_ids, " +
                "stat_offense, stat_flex, stat_defense, games, wins" +
                ") VALUES (" +
                ":championId, :teamPosition, :season, :tierGroup, :platformId, :queueId, :gameVersion, " +
                ":primaryRuneId, :primaryRuneIds, :secondaryRuneId, :secondaryRuneIds, " +
                ":statOffense, :statFlex, :statDefense, :games, :wins)";

        SqlParameterSource[] params = runeStats.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("championId", r.getChampionId())
                        .addValue("teamPosition", r.getTeamPosition())
                        .addValue("season", r.getSeason())
                        .addValue("tierGroup", r.getTierGroup())
                        .addValue("platformId", r.getPlatformId())
                        .addValue("queueId", r.getQueueId())
                        .addValue("gameVersion", r.getGameVersion())
                        .addValue("primaryRuneId", r.getPrimaryRuneId())
                        .addValue("primaryRuneIds", r.getPrimaryRuneIds())
                        .addValue("secondaryRuneId", r.getSecondaryRuneId())
                        .addValue("secondaryRuneIds", r.getSecondaryRuneIds())
                        .addValue("statOffense", r.getStatOffense())
                        .addValue("statFlex", r.getStatFlex())
                        .addValue("statDefense", r.getStatDefense())
                        .addValue("games", r.getGames())
                        .addValue("wins", r.getWins()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public void bulkSaveSpellStats(List<ChampionSpellStat> spellStats) {
        if (spellStats.isEmpty()) return;

        String sql = "INSERT INTO champion_spell_stat (" +
                "champion_id, team_position, season, tier_group, platform_id, queue_id, game_version, " +
                "spell1_id, spell2_id, games, wins" +
                ") VALUES (" +
                ":championId, :teamPosition, :season, :tierGroup, :platformId, :queueId, :gameVersion, " +
                ":spell1Id, :spell2Id, :games, :wins)";

        SqlParameterSource[] params = spellStats.stream()
                .map(s -> new MapSqlParameterSource()
                        .addValue("championId", s.getChampionId())
                        .addValue("teamPosition", s.getTeamPosition())
                        .addValue("season", s.getSeason())
                        .addValue("tierGroup", s.getTierGroup())
                        .addValue("platformId", s.getPlatformId())
                        .addValue("queueId", s.getQueueId())
                        .addValue("gameVersion", s.getGameVersion())
                        .addValue("spell1Id", s.getSpell1Id())
                        .addValue("spell2Id", s.getSpell2Id())
                        .addValue("games", s.getGames())
                        .addValue("wins", s.getWins()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public void bulkSaveSkillStats(List<ChampionSkillStat> skillStats) {
        if (skillStats.isEmpty()) return;

        String sql = "INSERT INTO champion_skill_stat (" +
                "champion_id, team_position, season, tier_group, platform_id, queue_id, game_version, " +
                "skill_order, skill_priority, games, wins" +
                ") VALUES (" +
                ":championId, :teamPosition, :season, :tierGroup, :platformId, :queueId, :gameVersion, " +
                ":skillOrder, :skillPriority, :games, :wins)";

        SqlParameterSource[] params = skillStats.stream()
                .map(s -> new MapSqlParameterSource()
                        .addValue("championId", s.getChampionId())
                        .addValue("teamPosition", s.getTeamPosition())
                        .addValue("season", s.getSeason())
                        .addValue("tierGroup", s.getTierGroup())
                        .addValue("platformId", s.getPlatformId())
                        .addValue("queueId", s.getQueueId())
                        .addValue("gameVersion", s.getGameVersion())
                        .addValue("skillOrder", s.getSkillOrder())
                        .addValue("skillPriority", s.getSkillPriority())
                        .addValue("games", s.getGames())
                        .addValue("wins", s.getWins()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public void bulkSaveItemStats(List<ChampionItemStat> itemStats) {
        if (itemStats.isEmpty()) return;

        String sql = "INSERT INTO champion_item_stat (" +
                "champion_id, team_position, season, tier_group, platform_id, queue_id, game_version, " +
                "build_type, item_ids, games, wins" +
                ") VALUES (" +
                ":championId, :teamPosition, :season, :tierGroup, :platformId, :queueId, :gameVersion, " +
                ":buildType, :itemIds, :games, :wins)";

        SqlParameterSource[] params = itemStats.stream()
                .map(i -> new MapSqlParameterSource()
                        .addValue("championId", i.getChampionId())
                        .addValue("teamPosition", i.getTeamPosition())
                        .addValue("season", i.getSeason())
                        .addValue("tierGroup", i.getTierGroup())
                        .addValue("platformId", i.getPlatformId())
                        .addValue("queueId", i.getQueueId())
                        .addValue("gameVersion", i.getGameVersion())
                        .addValue("buildType", i.getBuildType())
                        .addValue("itemIds", i.getItemIds())
                        .addValue("games", i.getGames())
                        .addValue("wins", i.getWins()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public void bulkSaveMatchupStats(List<ChampionMatchupStat> matchupStats) {
        if (matchupStats.isEmpty()) return;

        String sql = "INSERT INTO champion_matchup_stat (" +
                "champion_id, team_position, season, tier_group, platform_id, queue_id, game_version, " +
                "opponent_champion_id, games, wins, avg_kills, avg_deaths, avg_assists, avg_gold_diff" +
                ") VALUES (" +
                ":championId, :teamPosition, :season, :tierGroup, :platformId, :queueId, :gameVersion, " +
                ":opponentChampionId, :games, :wins, :avgKills, :avgDeaths, :avgAssists, :avgGoldDiff)";

        SqlParameterSource[] params = matchupStats.stream()
                .map(m -> new MapSqlParameterSource()
                        .addValue("championId", m.getChampionId())
                        .addValue("teamPosition", m.getTeamPosition())
                        .addValue("season", m.getSeason())
                        .addValue("tierGroup", m.getTierGroup())
                        .addValue("platformId", m.getPlatformId())
                        .addValue("queueId", m.getQueueId())
                        .addValue("gameVersion", m.getGameVersion())
                        .addValue("opponentChampionId", m.getOpponentChampionId())
                        .addValue("games", m.getGames())
                        .addValue("wins", m.getWins())
                        .addValue("avgKills", m.getAvgKills())
                        .addValue("avgDeaths", m.getAvgDeaths())
                        .addValue("avgAssists", m.getAvgAssists())
                        .addValue("avgGoldDiff", m.getAvgGoldDiff()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    // === 조회 ===

    @Override
    public List<ChampionStatSummary> findSummaries(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion) {
        return summaryJpaRepository.findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
                championId, teamPosition, season, tierGroup, platformId, queueId, gameVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ChampionRuneStat> findRuneStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion) {
        return runeStatJpaRepository.findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
                championId, teamPosition, season, tierGroup, platformId, queueId, gameVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ChampionSpellStat> findSpellStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion) {
        return spellStatJpaRepository.findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
                championId, teamPosition, season, tierGroup, platformId, queueId, gameVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ChampionSkillStat> findSkillStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion) {
        return skillStatJpaRepository.findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
                championId, teamPosition, season, tierGroup, platformId, queueId, gameVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ChampionItemStat> findItemStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion) {
        return itemStatJpaRepository.findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
                championId, teamPosition, season, tierGroup, platformId, queueId, gameVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ChampionMatchupStat> findMatchupStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion) {
        return matchupStatJpaRepository.findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
                championId, teamPosition, season, tierGroup, platformId, queueId, gameVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // === Entity → Domain 변환 ===

    private ChampionStatSummary toDomain(ChampionStatSummaryEntity e) {
        StatDimensionValue d = e.getDimension();
        return ChampionStatSummary.builder()
                .id(e.getId())
                .championId(d.getChampionId())
                .teamPosition(d.getTeamPosition())
                .season(d.getSeason())
                .tierGroup(d.getTierGroup())
                .platformId(d.getPlatformId())
                .queueId(d.getQueueId())
                .gameVersion(d.getGameVersion())
                .totalGames(e.getTotalGames())
                .wins(e.getWins())
                .totalBans(e.getTotalBans())
                .totalMatchesInDimension(e.getTotalMatchesInDimension())
                .avgKills(e.getAvgKills())
                .avgDeaths(e.getAvgDeaths())
                .avgAssists(e.getAvgAssists())
                .avgCs(e.getAvgCs())
                .avgGold(e.getAvgGold())
                .build();
    }

    private ChampionRuneStat toDomain(ChampionRuneStatEntity e) {
        StatDimensionValue d = e.getDimension();
        return ChampionRuneStat.builder()
                .id(e.getId())
                .championId(d.getChampionId())
                .teamPosition(d.getTeamPosition())
                .season(d.getSeason())
                .tierGroup(d.getTierGroup())
                .platformId(d.getPlatformId())
                .queueId(d.getQueueId())
                .gameVersion(d.getGameVersion())
                .primaryRuneId(e.getPrimaryRuneId())
                .primaryRuneIds(e.getPrimaryRuneIds())
                .secondaryRuneId(e.getSecondaryRuneId())
                .secondaryRuneIds(e.getSecondaryRuneIds())
                .statOffense(e.getStatOffense())
                .statFlex(e.getStatFlex())
                .statDefense(e.getStatDefense())
                .games(e.getGames())
                .wins(e.getWins())
                .build();
    }

    private ChampionSpellStat toDomain(ChampionSpellStatEntity e) {
        StatDimensionValue d = e.getDimension();
        return ChampionSpellStat.builder()
                .id(e.getId())
                .championId(d.getChampionId())
                .teamPosition(d.getTeamPosition())
                .season(d.getSeason())
                .tierGroup(d.getTierGroup())
                .platformId(d.getPlatformId())
                .queueId(d.getQueueId())
                .gameVersion(d.getGameVersion())
                .spell1Id(e.getSpell1Id())
                .spell2Id(e.getSpell2Id())
                .games(e.getGames())
                .wins(e.getWins())
                .build();
    }

    private ChampionSkillStat toDomain(ChampionSkillStatEntity e) {
        StatDimensionValue d = e.getDimension();
        return ChampionSkillStat.builder()
                .id(e.getId())
                .championId(d.getChampionId())
                .teamPosition(d.getTeamPosition())
                .season(d.getSeason())
                .tierGroup(d.getTierGroup())
                .platformId(d.getPlatformId())
                .queueId(d.getQueueId())
                .gameVersion(d.getGameVersion())
                .skillOrder(e.getSkillOrder())
                .skillPriority(e.getSkillPriority())
                .games(e.getGames())
                .wins(e.getWins())
                .build();
    }

    private ChampionItemStat toDomain(ChampionItemStatEntity e) {
        StatDimensionValue d = e.getDimension();
        return ChampionItemStat.builder()
                .id(e.getId())
                .championId(d.getChampionId())
                .teamPosition(d.getTeamPosition())
                .season(d.getSeason())
                .tierGroup(d.getTierGroup())
                .platformId(d.getPlatformId())
                .queueId(d.getQueueId())
                .gameVersion(d.getGameVersion())
                .buildType(e.getBuildType())
                .itemIds(e.getItemIds())
                .games(e.getGames())
                .wins(e.getWins())
                .build();
    }

    private ChampionMatchupStat toDomain(ChampionMatchupStatEntity e) {
        StatDimensionValue d = e.getDimension();
        return ChampionMatchupStat.builder()
                .id(e.getId())
                .championId(d.getChampionId())
                .teamPosition(d.getTeamPosition())
                .season(d.getSeason())
                .tierGroup(d.getTierGroup())
                .platformId(d.getPlatformId())
                .queueId(d.getQueueId())
                .gameVersion(d.getGameVersion())
                .opponentChampionId(e.getOpponentChampionId())
                .games(e.getGames())
                .wins(e.getWins())
                .avgKills(e.getAvgKills())
                .avgDeaths(e.getAvgDeaths())
                .avgAssists(e.getAvgAssists())
                .avgGoldDiff(e.getAvgGoldDiff())
                .build();
    }
}
