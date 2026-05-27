package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.ParticipantFrameEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Set<String> findExistingMatchIds(List<String> matchIds) {
        if (matchIds.isEmpty()) return Collections.emptySet();

        String sql = "SELECT DISTINCT match_id FROM participant_frame WHERE match_id IN (:matchIds)";
        MapSqlParameterSource params = new MapSqlParameterSource("matchIds", matchIds);
        List<String> result = jdbcTemplate.queryForList(sql, params, String.class);
        return new HashSet<>(result);
    }
}
