package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.ParticipantFrameEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.TimeLineEventEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.events.*;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.ChampionStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.DamageStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import com.mmrtr.lol.infra.persistence.match.repository.TimeLineRepositoryImpl;
import com.mmrtr.lol.infra.riot.dto.match_timeline.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeLineService {

    private final TimeLineRepositoryImpl timeLineRepository;

    @Transactional
    public void saveAll(List<MatchEntity> matchEntities, List<TimelineDto> timelineDtos) {
        Map<String, MatchEntity> matchEntityMap = matchEntities.stream()
                .collect(Collectors.toMap(MatchEntity::getMatchId, Function.identity()));

        List<TimeLineEventEntity> allTimeLineEvents = new ArrayList<>();
        List<ParticipantFrameEntity> allParticipantFrames = new ArrayList<>();
        List<ItemEventsEntity> allItemEvents = new ArrayList<>();
        List<SkillEventsEntity> allSkillEvents = new ArrayList<>();
        List<KillEventsEntity> allKillEvents = new ArrayList<>();
        List<BuildingEventsEntity> allBuildingEvents = new ArrayList<>();
        List<WardEventsEntity> allWardEvents = new ArrayList<>();
        List<GameEventsEntity> allGameEvents = new ArrayList<>();
        List<LevelEventsEntity> allLevelEvents = new ArrayList<>();
        List<ChampionSpecialKillEventEntity> allChampionSpecialKillEvents = new ArrayList<>();
        List<TurretPlateDestroyedEventEntity> allTurretPlateDestroyedEvents = new ArrayList<>();

        for (TimelineDto timelineDto : timelineDtos) {
            if (timelineDto == null || timelineDto.getInfo() == null) continue;

            String matchId = timelineDto.getMetadata().getMatchId();
            MatchEntity matchEntity = matchEntityMap.get(matchId);
            if (matchEntity == null) continue;

            List<FramesTimeLineDto> frames = timelineDto.getInfo().getFrames();
            if (frames == null) continue;

            for (FramesTimeLineDto frame : frames) {
                TimeLineEventEntity timeLineEvent = TimeLineEventEntity.builder()
                        .matchEntity(matchEntity)
                        .timestamp(frame.getTimestamp())
                        .build();
                allTimeLineEvents.add(timeLineEvent);

                // Participant frames
                ParticipantFramesDto participantFrames = frame.getParticipantFrames();
                if (participantFrames != null) {
                    List<ParticipantFrameDto> frameDtos = toParticipantFrameList(participantFrames);
                    for (ParticipantFrameDto pf : frameDtos) {
                        allParticipantFrames.add(toParticipantFrameEntity(matchEntity, frame.getTimestamp(), pf));
                    }
                }

                // Events classification
                List<EventsTimeLineDto> events = frame.getEvents();
                if (events == null) continue;

                for (EventsTimeLineDto event : events) {
                    String type = event.getType();
                    if (type == null) continue;

                    switch (type) {
                        case "ITEM_PURCHASED", "ITEM_SOLD", "ITEM_DESTROYED", "ITEM_UNDO" ->
                                allItemEvents.add(toItemEventsEntity(timeLineEvent, event));
                        case "SKILL_LEVEL_UP" ->
                                allSkillEvents.add(toSkillEventsEntity(timeLineEvent, event));
                        case "CHAMPION_KILL" ->
                                allKillEvents.add(toKillEventsEntity(timeLineEvent, event));
                        case "BUILDING_KILL", "ELITE_MONSTER_KILL" ->
                                allBuildingEvents.add(toBuildingEventsEntity(timeLineEvent, event));
                        case "WARD_PLACED", "WARD_KILL" ->
                                allWardEvents.add(toWardEventsEntity(timeLineEvent, event));
                        case "GAME_END" ->
                                allGameEvents.add(toGameEventsEntity(timeLineEvent, event, timelineDto));
                        case "LEVEL_UP" ->
                                allLevelEvents.add(toLevelEventsEntity(timeLineEvent, event));
                        case "CHAMPION_SPECIAL_KILL" ->
                                allChampionSpecialKillEvents.add(toChampionSpecialKillEventEntity(timeLineEvent, event));
                        case "TURRET_PLATE_DESTROYED" ->
                                allTurretPlateDestroyedEvents.add(toTurretPlateDestroyedEventEntity(timeLineEvent, event));
                        default -> { }
                    }
                }
            }
        }

        timeLineRepository.bulkSaveTimeLineEvents(allTimeLineEvents);
        timeLineRepository.bulkSaveParticipantFrames(allParticipantFrames);
        timeLineRepository.bulkSaveItemEvents(allItemEvents);
        timeLineRepository.bulkSaveSkillEvents(allSkillEvents);
        timeLineRepository.bulkSaveKillEvents(allKillEvents);
        timeLineRepository.bulkSaveBuildingEvents(allBuildingEvents);
        timeLineRepository.bulkSaveWardEvents(allWardEvents);
        timeLineRepository.bulkSaveGameEvents(allGameEvents);
        timeLineRepository.bulkSaveLevelEvents(allLevelEvents);
        timeLineRepository.bulkSaveChampionSpecialKillEvents(allChampionSpecialKillEvents);
        timeLineRepository.bulkSaveTurretPlateDestroyedEvents(allTurretPlateDestroyedEvents);
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
                .matchEntity(matchEntity)
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

    private ItemEventsEntity toItemEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        ItemEventsEntity entity = new ItemEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setItemId(event.getItemId());
        entity.setParticipantId(event.getParticipantId());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        entity.setAfterId(event.getAfterId());
        entity.setBeforeId(event.getBeforeId());
        entity.setGoldGain(event.getGoldGain());
        return entity;
    }

    private SkillEventsEntity toSkillEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        SkillEventsEntity entity = new SkillEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setSkillSlot(event.getSkillSlot());
        entity.setParticipantId(event.getParticipantId());
        entity.setLevelUpType(event.getLevelUpType());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        return entity;
    }

    private KillEventsEntity toKillEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        KillEventsEntity entity = new KillEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setAssistingParticipantIds(convertAssistingIds(event.getAssistingParticipantIds()));
        entity.setBounty(event.getBounty());
        entity.setKillStreakLength(event.getKillStreakLength());
        entity.setKillerId(event.getKillerId());
        entity.setPosition(event.getPosition() != null
                ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                : new PositionValue());
        entity.setShutdownBounty(0);
        entity.setVictimId(event.getVictimId());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        return entity;
    }

    private BuildingEventsEntity toBuildingEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        BuildingEventsEntity entity = new BuildingEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setAssistingParticipantIds(convertAssistingIds(event.getAssistingParticipantIds()));
        entity.setBounty(event.getBounty());
        entity.setBuildingType(event.getBuildingType());
        entity.setKillerId(event.getKillerId());
        entity.setLaneType(event.getLaneType());
        entity.setPositionValue(event.getPosition() != null
                ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                : new PositionValue());
        entity.setTeamId(event.getTeamId());
        entity.setTimestamp(event.getTimestamp());
        entity.setTowerType(event.getTowerType());
        entity.setType(event.getType());
        return entity;
    }

    private WardEventsEntity toWardEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        WardEventsEntity entity = new WardEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setParticipantId(event.getParticipantId());
        entity.setWardType(event.getWardType());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        return entity;
    }

    private GameEventsEntity toGameEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event, TimelineDto timelineDto) {
        GameEventsEntity entity = new GameEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setTimestamp(event.getTimestamp());
        entity.setGameId(timelineDto.getInfo().getGameId());
        entity.setRealTimestamp(0L);
        entity.setWinningTeam(0);
        entity.setType(event.getType());
        return entity;
    }

    private LevelEventsEntity toLevelEventsEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        LevelEventsEntity entity = new LevelEventsEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setLevel(0);
        entity.setParticipantId(event.getParticipantId());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        return entity;
    }

    private ChampionSpecialKillEventEntity toChampionSpecialKillEventEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        ChampionSpecialKillEventEntity entity = new ChampionSpecialKillEventEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setKillType(event.getKillType());
        entity.setKillerId(event.getKillerId());
        entity.setMultiKillLength(0);
        entity.setPositionValue(event.getPosition() != null
                ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                : new PositionValue());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        return entity;
    }

    private TurretPlateDestroyedEventEntity toTurretPlateDestroyedEventEntity(TimeLineEventEntity timeLineEvent, EventsTimeLineDto event) {
        TurretPlateDestroyedEventEntity entity = new TurretPlateDestroyedEventEntity();
        entity.setTimeLineEvent(timeLineEvent);
        entity.setKillerId(event.getKillerId());
        entity.setLaneType(event.getLaneType());
        entity.setPositionValue(event.getPosition() != null
                ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                : new PositionValue());
        entity.setTeamId(event.getTeamId());
        entity.setTimestamp(event.getTimestamp());
        entity.setType(event.getType());
        return entity;
    }

    private String convertAssistingIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
