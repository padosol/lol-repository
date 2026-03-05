package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.ParticipantFrameEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.events.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class TimeLineRepositoryImpl {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void bulkSaveParticipantFrames(List<ParticipantFrameEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO participant_frame (" +
                "match_id, timestamp, participant_id, " +
                "ability_haste, ability_power, armor, armor_pen, armor_pen_percent, " +
                "attack_damage, attack_speed, bonus_armor_pen_percent, bonus_magic_pen_percent, " +
                "cc_reduction, cooldown_reduction, health, health_max, health_regen, " +
                "lifesteal, magic_pen, magic_pen_percent, magic_resist, movement_speed, " +
                "omnivamp, physical_vamp, power, power_max, power_regen, spell_vamp, " +
                "current_gold, gold_per_second, jungle_minions_killed, level, minions_killed, " +
                "time_enemy_spent_controlled, total_gold, xp, " +
                "magic_damage_done, magic_damage_done_to_champions, magic_damage_taken, " +
                "physical_damage_done, physical_damage_done_to_champions, physical_damage_taken, " +
                "total_damage_done, total_damage_done_to_champions, total_damage_taken, " +
                "true_damage_done, true_damage_done_to_champions, true_damage_taken, " +
                "x, y" +
                ") VALUES (" +
                ":matchId, :timestamp, :participantId, " +
                ":abilityHaste, :abilityPower, :armor, :armorPen, :armorPenPercent, " +
                ":attackDamage, :attackSpeed, :bonusArmorPenPercent, :bonusMagicPenPercent, " +
                ":ccReduction, :cooldownReduction, :health, :healthMax, :healthRegen, " +
                ":lifesteal, :magicPen, :magicPenPercent, :magicResist, :movementSpeed, " +
                ":omnivamp, :physicalVamp, :power, :powerMax, :powerRegen, :spellVamp, " +
                ":currentGold, :goldPerSecond, :jungleMinionsKilled, :level, :minionsKilled, " +
                ":timeEnemySpentControlled, :totalGold, :xp, " +
                ":magicDamageDone, :magicDamageDoneToChampions, :magicDamageTaken, " +
                ":physicalDamageDone, :physicalDamageDoneToChampions, :physicalDamageTaken, " +
                ":totalDamageDone, :totalDamageDoneToChampions, :totalDamageTaken, " +
                ":trueDamageDone, :trueDamageDoneToChampions, :trueDamageTaken, " +
                ":x, :y" +
                ") ON CONFLICT (match_id, timestamp, participant_id) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("participantId", e.getParticipantId())
                        .addValue("abilityHaste", e.getChampionStats().getAbilityHaste())
                        .addValue("abilityPower", e.getChampionStats().getAbilityPower())
                        .addValue("armor", e.getChampionStats().getArmor())
                        .addValue("armorPen", e.getChampionStats().getArmorPen())
                        .addValue("armorPenPercent", e.getChampionStats().getArmorPenPercent())
                        .addValue("attackDamage", e.getChampionStats().getAttackDamage())
                        .addValue("attackSpeed", e.getChampionStats().getAttackSpeed())
                        .addValue("bonusArmorPenPercent", e.getChampionStats().getBonusArmorPenPercent())
                        .addValue("bonusMagicPenPercent", e.getChampionStats().getBonusMagicPenPercent())
                        .addValue("ccReduction", e.getChampionStats().getCcReduction())
                        .addValue("cooldownReduction", e.getChampionStats().getCooldownReduction())
                        .addValue("health", e.getChampionStats().getHealth())
                        .addValue("healthMax", e.getChampionStats().getHealthMax())
                        .addValue("healthRegen", e.getChampionStats().getHealthRegen())
                        .addValue("lifesteal", e.getChampionStats().getLifesteal())
                        .addValue("magicPen", e.getChampionStats().getMagicPen())
                        .addValue("magicPenPercent", e.getChampionStats().getMagicPenPercent())
                        .addValue("magicResist", e.getChampionStats().getMagicResist())
                        .addValue("movementSpeed", e.getChampionStats().getMovementSpeed())
                        .addValue("omnivamp", e.getChampionStats().getOmnivamp())
                        .addValue("physicalVamp", e.getChampionStats().getPhysicalVamp())
                        .addValue("power", e.getChampionStats().getPower())
                        .addValue("powerMax", e.getChampionStats().getPowerMax())
                        .addValue("powerRegen", e.getChampionStats().getPowerRegen())
                        .addValue("spellVamp", e.getChampionStats().getSpellVamp())
                        .addValue("currentGold", e.getCurrentGold())
                        .addValue("goldPerSecond", e.getGoldPerSecond())
                        .addValue("jungleMinionsKilled", e.getJungleMinionsKilled())
                        .addValue("level", e.getLevel())
                        .addValue("minionsKilled", e.getMinionsKilled())
                        .addValue("timeEnemySpentControlled", e.getTimeEnemySpentControlled())
                        .addValue("totalGold", e.getTotalGold())
                        .addValue("xp", e.getXp())
                        .addValue("magicDamageDone", e.getDamageStats().getMagicDamageDone())
                        .addValue("magicDamageDoneToChampions", e.getDamageStats().getMagicDamageDoneToChampions())
                        .addValue("magicDamageTaken", e.getDamageStats().getMagicDamageTaken())
                        .addValue("physicalDamageDone", e.getDamageStats().getPhysicalDamageDone())
                        .addValue("physicalDamageDoneToChampions", e.getDamageStats().getPhysicalDamageDoneToChampions())
                        .addValue("physicalDamageTaken", e.getDamageStats().getPhysicalDamageTaken())
                        .addValue("totalDamageDone", e.getDamageStats().getTotalDamageDone())
                        .addValue("totalDamageDoneToChampions", e.getDamageStats().getTotalDamageDoneToChampions())
                        .addValue("totalDamageTaken", e.getDamageStats().getTotalDamageTaken())
                        .addValue("trueDamageDone", e.getDamageStats().getTrueDamageDone())
                        .addValue("trueDamageDoneToChampions", e.getDamageStats().getTrueDamageDoneToChampions())
                        .addValue("trueDamageTaken", e.getDamageStats().getTrueDamageTaken())
                        .addValue("x", e.getPosition().getX())
                        .addValue("y", e.getPosition().getY()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveItemEvents(List<ItemEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO item_event (" +
                "match_id, item_id, participant_id, timestamp, type, after_id, before_id, gold_gain, event_index" +
                ") VALUES (" +
                ":matchId, :itemId, :participantId, :timestamp, :type, :afterId, :beforeId, :goldGain, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("itemId", e.getItemId())
                        .addValue("participantId", e.getParticipantId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("type", e.getType())
                        .addValue("afterId", e.getAfterId())
                        .addValue("beforeId", e.getBeforeId())
                        .addValue("goldGain", e.getGoldGain())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveSkillEvents(List<SkillEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO skill_level_up_event (" +
                "match_id, skill_slot, participant_id, level_up_type, timestamp, event_index" +
                ") VALUES (" +
                ":matchId, :skillSlot, :participantId, :levelUpType, :timestamp, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("skillSlot", e.getSkillSlot())
                        .addValue("participantId", e.getParticipantId())
                        .addValue("levelUpType", e.getLevelUpType())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveKillEvents(List<KillEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO kill_event (" +
                "match_id, assisting_participant_ids, bounty, kill_streak_length, " +
                "killer_id, x, y, shutdown_bounty, victim_damage_dealt, victim_damage_received, " +
                "victim_id, timestamp, event_index" +
                ") VALUES (" +
                ":matchId, :assistingParticipantIds, :bounty, :killStreakLength, " +
                ":killerId, :x, :y, :shutdownBounty, :victimDamageDealt, :victimDamageReceived, " +
                ":victimId, :timestamp, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("assistingParticipantIds", e.getAssistingParticipantIds())
                        .addValue("bounty", e.getBounty())
                        .addValue("killStreakLength", e.getKillStreakLength())
                        .addValue("killerId", e.getKillerId())
                        .addValue("x", e.getPosition().getX())
                        .addValue("y", e.getPosition().getY())
                        .addValue("shutdownBounty", e.getShutdownBounty())
                        .addValue("victimDamageDealt", e.getVictimDamageDealt())
                        .addValue("victimDamageReceived", e.getVictimDamageReceived())
                        .addValue("victimId", e.getVictimId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveBuildingEvents(List<BuildingEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO building_events (" +
                "match_id, assisting_participant_ids, bounty, building_type, " +
                "killer_id, lane_type, x, y, team_id, timestamp, tower_type, event_index" +
                ") VALUES (" +
                ":matchId, :assistingParticipantIds, :bounty, :buildingType, " +
                ":killerId, :laneType, :x, :y, :teamId, :timestamp, :towerType, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("assistingParticipantIds", e.getAssistingParticipantIds())
                        .addValue("bounty", e.getBounty())
                        .addValue("buildingType", e.getBuildingType())
                        .addValue("killerId", e.getKillerId())
                        .addValue("laneType", e.getLaneType())
                        .addValue("x", e.getPosition() != null ? e.getPosition().getX() : 0)
                        .addValue("y", e.getPosition() != null ? e.getPosition().getY() : 0)
                        .addValue("teamId", e.getTeamId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("towerType", e.getTowerType())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveWardEvents(List<WardEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO ward_event (" +
                "match_id, creator_id, ward_type, timestamp, event_index" +
                ") VALUES (" +
                ":matchId, :creatorId, :wardType, :timestamp, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("creatorId", e.getCreatorId())
                        .addValue("wardType", e.getWardType())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveGameEvents(List<GameEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO game_end_event (" +
                "match_id, timestamp, game_id, real_timestamp, winning_team, event_index" +
                ") VALUES (" +
                ":matchId, :timestamp, :gameId, :realTimestamp, :winningTeam, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("gameId", e.getGameId())
                        .addValue("realTimestamp", e.getRealTimestamp())
                        .addValue("winningTeam", e.getWinningTeam())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveLevelEvents(List<LevelEventsEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO level_up_event (" +
                "match_id, level, participant_id, timestamp, event_index" +
                ") VALUES (" +
                ":matchId, :level, :participantId, :timestamp, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("level", e.getLevel())
                        .addValue("participantId", e.getParticipantId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveChampionSpecialKillEvents(List<ChampionSpecialKillEventEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO champion_special_kill_event (" +
                "match_id, kill_type, killer_id, multi_kill_length, x, y, timestamp, event_index" +
                ") VALUES (" +
                ":matchId, :killType, :killerId, :multiKillLength, :x, :y, :timestamp, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("killType", e.getKillType())
                        .addValue("killerId", e.getKillerId())
                        .addValue("multiKillLength", e.getMultiKillLength())
                        .addValue("x", e.getPosition() != null ? e.getPosition().getX() : 0)
                        .addValue("y", e.getPosition() != null ? e.getPosition().getY() : 0)
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public void bulkSaveTurretPlateDestroyedEvents(List<TurretPlateDestroyedEventEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO turret_plate_destroyed_event (" +
                "match_id, killer_id, lane_type, x, y, team_id, timestamp, event_index" +
                ") VALUES (" +
                ":matchId, :killerId, :laneType, :x, :y, :teamId, :timestamp, :eventIndex" +
                ") ON CONFLICT (match_id, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("killerId", e.getKillerId())
                        .addValue("laneType", e.getLaneType())
                        .addValue("x", e.getPosition() != null ? e.getPosition().getX() : 0)
                        .addValue("y", e.getPosition() != null ? e.getPosition().getY() : 0)
                        .addValue("teamId", e.getTeamId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public Set<String> findExistingMatchIds(List<String> matchIds) {
        if (matchIds.isEmpty()) return Collections.emptySet();

        String sql = "SELECT DISTINCT match_id FROM item_event WHERE match_id IN (:matchIds)";
        MapSqlParameterSource params = new MapSqlParameterSource("matchIds", matchIds);
        List<String> result = jdbcTemplate.queryForList(sql, params, String.class);
        return new HashSet<>(result);
    }
}
