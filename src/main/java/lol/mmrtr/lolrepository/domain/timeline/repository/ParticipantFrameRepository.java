package lol.mmrtr.lolrepository.domain.timeline.repository;

import lol.mmrtr.lolrepository.domain.entity.ParticipantFrame;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ParticipantFrameRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ParticipantFrameRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void bulkSave(List<ParticipantFrame> participantFrames) {

        String sql = " INSERT INTO participant_frame (" +
                "match_id," +
                "timestamp," +
                "ability_haste," +
                "ability_power," +
                "armor," +
                "armor_pen," +
                "armor_pen_percent," +
                "attack_damage," +
                "attack_speed," +
                "bonus_armor_pen_percent," +
                "bonus_magic_pen_percent," +
                "cc_reduction," +
                "cooldown_reduction," +
                "health," +
                "health_max," +
                "health_regen," +
                "lifesteal," +
                "magic_pen," +
                "magic_pen_percent," +
                "magic_resist," +
                "movement_speed," +
                "omnivamp," +
                "physical_vamp," +
                "power," +
                "power_max," +
                "power_regen," +
                "spell_vamp," +
                "current_gold," +
                "magic_damage_done," +
                "magic_damage_done_to_champions," +
                "magic_damage_taken," +
                "physical_damage_done," +
                "physical_damage_done_to_champions," +
                "physical_damage_taken," +
                "total_damage_done," +
                "total_damage_done_to_champions," +
                "total_damage_taken," +
                "true_damage_done," +
                "true_damage_done_to_champions," +
                "true_damage_taken," +
                "gold_per_second," +
                "jungle_minions_killed," +
                "level," +
                "minions_killed," +
                "participant_id," +
                "x," +
                "y," +
                "time_enemy_spent_controlled," +
                "total_gold," +
                "xp "+
                ") VALUES (" +
                ":matchId," +
                ":timestamp," +
                ":abilityHaste," +
                ":abilityPower," +
                ":armor," +
                ":armorPen," +
                ":armorPenPercent," +
                ":attackDamage," +
                ":attackSpeed," +
                ":bonusArmorPenPercent," +
                ":bonusMagicPenPercent," +
                ":ccReduction," +
                ":cooldownReduction," +
                ":health," +
                ":healthMax," +
                ":healthRegen," +
                ":lifesteal," +
                ":magicPen," +
                ":magicPenPercent," +
                ":magicResist," +
                ":movementSpeed," +
                ":omnivamp," +
                ":physicalVamp," +
                ":power," +
                ":powerMax," +
                ":powerRegen," +
                ":spellVamp," +
                ":currentGold," +
                ":magicDamageDone," +
                ":magicDamageDoneToChampions," +
                ":magicDamageTaken," +
                ":physicalDamageDone," +
                ":physicalDamageDoneToChampions," +
                ":physicalDamageTaken," +
                ":totalDamageDone," +
                ":totalDamageDoneToChampions," +
                ":totalDamageTaken," +
                ":trueDamageDone," +
                ":trueDamageDoneToChampions," +
                ":trueDamageTaken," +
                ":goldPerSecond," +
                ":jungleMinionsKilled," +
                ":level," +
                ":minionsKilled," +
                ":participantId," +
                ":x," +
                ":y," +
                ":timeEnemySpentControlled," +
                ":totalGold," +
                ":xp "+
                ") ON CONFLICT (participant_id, timestamp , match_id) DO NOTHING";

        SqlParameterSource[] param = SqlParameterSourceUtils.createBatch(participantFrames);
        jdbcTemplate.batchUpdate(sql, param);
    }


}
