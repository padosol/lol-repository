package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.ParticipantFrameEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.ChampionStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.DamageStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import com.mmrtr.lol.infra.persistence.match.repository.TimeLineRepositoryImpl;
import com.mmrtr.lol.domain.match.readmodel.timeline.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeLineService {

    private final TimeLineRepositoryImpl timeLineRepository;

    public void saveAll(List<MatchEntity> matchEntities, List<TimelineDto> timelineDtos) {
        long start = System.currentTimeMillis();

        Map<String, MatchEntity> matchEntityMap = matchEntities.stream()
                .collect(Collectors.toMap(MatchEntity::getMatchId, Function.identity()));

        Set<String> existingMatchIds = timeLineRepository.findExistingMatchIds(
                matchEntities.stream().map(MatchEntity::getMatchId).toList()
        );

        List<ParticipantFrameEntity> allParticipantFrames = new ArrayList<>();

        for (TimelineDto timelineDto : timelineDtos) {
            if (timelineDto == null || timelineDto.getInfo() == null) continue;

            String matchId = timelineDto.getMetadata().getMatchId();
            MatchEntity matchEntity = matchEntityMap.get(matchId);
            if (matchEntity == null) continue;

            if (existingMatchIds.contains(matchId)) {
                log.debug("[timeline] matchId {} 이미 존재, 스킵", matchId);
                continue;
            }

            List<FramesTimeLineDto> frames = timelineDto.getInfo().getFrames();
            if (frames == null) continue;

            for (FramesTimeLineDto frame : frames) {
                ParticipantFramesDto participantFrames = frame.getParticipantFrames();
                if (participantFrames == null) continue;
                for (ParticipantFrameDto pf : toParticipantFrameList(participantFrames)) {
                    allParticipantFrames.add(toParticipantFrameEntity(matchEntity, frame.getTimestamp(), pf));
                }
            }
        }

        if (!allParticipantFrames.isEmpty()) {
            long t = System.currentTimeMillis();
            timeLineRepository.bulkSaveParticipantFrames(allParticipantFrames);
            log.debug("[timeline] bulkSave participantFrames: {}ms ({}건)",
                    System.currentTimeMillis() - t, allParticipantFrames.size());
        }

        log.debug("[timeline] 총 소요: {}ms", System.currentTimeMillis() - start);
    }

    private List<ParticipantFrameDto> toParticipantFrameList(ParticipantFramesDto frames) {
        List<ParticipantFrameDto> list = new ArrayList<>();
        if (frames.getP1() != null) list.add(frames.getP1());
        if (frames.getP2() != null) list.add(frames.getP2());
        if (frames.getP3() != null) list.add(frames.getP3());
        if (frames.getP4() != null) list.add(frames.getP4());
        if (frames.getP5() != null) list.add(frames.getP5());
        if (frames.getP6() != null) list.add(frames.getP6());
        if (frames.getP7() != null) list.add(frames.getP7());
        if (frames.getP8() != null) list.add(frames.getP8());
        if (frames.getP9() != null) list.add(frames.getP9());
        if (frames.getP10() != null) list.add(frames.getP10());
        return list;
    }

    private ParticipantFrameEntity toParticipantFrameEntity(MatchEntity matchEntity, int timestamp, ParticipantFrameDto dto) {
        ChampionStatsDto cs = dto.getChampionStats();
        DamageStatsDto ds = dto.getDamageStats();
        PositionDto pos = dto.getPosition();

        return ParticipantFrameEntity.builder()
                .matchId(matchEntity.getMatchId())
                .timestamp(timestamp)
                .participantId(dto.getParticipantId())
                .championStats(cs != null ? ChampionStatsValue.builder()
                        .abilityHaste(cs.getAbilityHaste())
                        .abilityPower(cs.getAbilityPower())
                        .armor(cs.getArmor())
                        .armorPen(cs.getArmorPen())
                        .armorPenPercent(cs.getArmorPenPercent())
                        .attackDamage(cs.getAttackDamage())
                        .attackSpeed(cs.getAttackSpeed())
                        .bonusArmorPenPercent(cs.getBonusArmorPenPercent())
                        .bonusMagicPenPercent(cs.getBonusMagicPenPercent())
                        .ccReduction(cs.getCcReduction())
                        .cooldownReduction(cs.getCooldownReduction())
                        .health(cs.getHealth())
                        .healthMax(cs.getHealthMax())
                        .healthRegen(cs.getHealthRegen())
                        .lifesteal(cs.getLifesteal())
                        .magicPen(cs.getMagicPen())
                        .magicPenPercent(cs.getMagicPenPercent())
                        .magicResist(cs.getMagicResist())
                        .movementSpeed(cs.getMovementSpeed())
                        .omnivamp(cs.getOmnivamp())
                        .physicalVamp(cs.getPhysicalVamp())
                        .power(cs.getPower())
                        .powerMax(cs.getPowerMax())
                        .powerRegen(cs.getPowerRegen())
                        .spellVamp(cs.getSpellVamp())
                        .build() : new ChampionStatsValue())
                .currentGold(dto.getCurrentGold())
                .damageStats(ds != null ? DamageStatsValue.builder()
                        .magicDamageDone(ds.getMagicDamageDone())
                        .magicDamageDoneToChampions(ds.getMagicDamageDoneToChampions())
                        .magicDamageTaken(ds.getMagicDamageTaken())
                        .physicalDamageDone(ds.getPhysicalDamageDone())
                        .physicalDamageDoneToChampions(ds.getPhysicalDamageDoneToChampions())
                        .physicalDamageTaken(ds.getPhysicalDamageTaken())
                        .totalDamageDone(ds.getTotalDamageDone())
                        .totalDamageDoneToChampions(ds.getTotalDamageDoneToChampions())
                        .totalDamageTaken(ds.getTotalDamageTaken())
                        .trueDamageDone(ds.getTrueDamageDone())
                        .trueDamageDoneToChampions(ds.getTrueDamageDoneToChampions())
                        .trueDamageTaken(ds.getTrueDamageTaken())
                        .build() : new DamageStatsValue())
                .goldPerSecond(dto.getGoldPerSecond())
                .jungleMinionsKilled(dto.getJungleMinionsKilled())
                .level(dto.getLevel())
                .minionsKilled(dto.getMinionsKilled())
                .position(pos != null ? PositionValue.builder()
                        .x(pos.getX())
                        .y(pos.getY())
                        .build() : new PositionValue())
                .timeEnemySpentControlled(dto.getTimeEnemySpentControlled())
                .totalGold(dto.getTotalGold())
                .xp(dto.getXp())
                .build();
    }
}
