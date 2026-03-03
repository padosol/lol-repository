package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.ParticipantFrameEntity;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.events.*;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.ChampionStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.DamageStatsValue;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.value.PositionValue;
import com.mmrtr.lol.infra.persistence.match.repository.TimeLineRepositoryImpl;
import com.mmrtr.lol.infra.riot.dto.match_timeline.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeLineService {

    private final TimeLineRepositoryImpl timeLineRepository;
    private final Executor timelineSaveExecutor;

    public void saveAll(List<MatchEntity> matchEntities, List<TimelineDto> timelineDtos) {
        long start = System.currentTimeMillis();

        // 엔티티 매핑
        long t = System.currentTimeMillis();
        Map<String, MatchEntity> matchEntityMap = matchEntities.stream()
                .collect(Collectors.toMap(MatchEntity::getMatchId, Function.identity()));

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
                                allItemEvents.add(toItemEventsEntity(matchEntity.getMatchId(), event));
                        case "SKILL_LEVEL_UP" ->
                                allSkillEvents.add(toSkillEventsEntity(matchEntity.getMatchId(), event));
                        case "CHAMPION_KILL" ->
                                allKillEvents.add(toKillEventsEntity(matchEntity.getMatchId(), event));
                        case "BUILDING_KILL", "ELITE_MONSTER_KILL" ->
                                allBuildingEvents.add(toBuildingEventsEntity(matchEntity.getMatchId(), event));
                        case "WARD_PLACED", "WARD_KILL" ->
                                allWardEvents.add(toWardEventsEntity(matchEntity.getMatchId(), event));
                        case "GAME_END" ->
                                allGameEvents.add(toGameEventsEntity(matchEntity.getMatchId(), event, timelineDto));
                        case "LEVEL_UP" ->
                                allLevelEvents.add(toLevelEventsEntity(matchEntity.getMatchId(), event));
                        case "CHAMPION_SPECIAL_KILL" ->
                                allChampionSpecialKillEvents.add(toChampionSpecialKillEventEntity(matchEntity.getMatchId(), event));
                        case "TURRET_PLATE_DESTROYED" ->
                                allTurretPlateDestroyedEvents.add(toTurretPlateDestroyedEventEntity(matchEntity.getMatchId(), event));
                        default -> { }
                    }
                }
            }
        }
        log.debug("[timeline] 엔티티 매핑: {}ms", System.currentTimeMillis() - t);

        // Phase 2: 병렬 bulk save (모두 독립적)
        CompletableFuture<?>[] futures = {
            runBulkSave("participantFrames", allParticipantFrames,
                    timeLineRepository::bulkSaveParticipantFrames),
            runBulkSave("itemEvents", allItemEvents,
                    timeLineRepository::bulkSaveItemEvents),
            runBulkSave("skillEvents", allSkillEvents,
                    timeLineRepository::bulkSaveSkillEvents),
            runBulkSave("killEvents", allKillEvents,
                    timeLineRepository::bulkSaveKillEvents),
            runBulkSave("buildingEvents", allBuildingEvents,
                    timeLineRepository::bulkSaveBuildingEvents),
            runBulkSave("wardEvents", allWardEvents,
                    timeLineRepository::bulkSaveWardEvents),
            runBulkSave("gameEvents", allGameEvents,
                    timeLineRepository::bulkSaveGameEvents),
            runBulkSave("levelEvents", allLevelEvents,
                    timeLineRepository::bulkSaveLevelEvents),
            runBulkSave("championSpecialKillEvents", allChampionSpecialKillEvents,
                    timeLineRepository::bulkSaveChampionSpecialKillEvents),
            runBulkSave("turretPlateDestroyedEvents", allTurretPlateDestroyedEvents,
                    timeLineRepository::bulkSaveTurretPlateDestroyedEvents),
        };

        CompletableFuture.allOf(futures).join();

        log.debug("[timeline] 총 소요: {}ms", System.currentTimeMillis() - start);
    }

    private <T> CompletableFuture<Void> runBulkSave(String name, List<T> entities,
                                                      Consumer<List<T>> saveFunction) {
        if (entities.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            long t = System.currentTimeMillis();
            saveFunction.accept(entities);
            log.debug("[timeline] bulkSave {}: {}ms ({}건)", name,
                    System.currentTimeMillis() - t, entities.size());
        }, timelineSaveExecutor);
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

    private ItemEventsEntity toItemEventsEntity(String matchId, EventsTimeLineDto event) {
        return ItemEventsEntity.builder()
                .matchId(matchId)
                .itemId(event.getItemId())
                .participantId(event.getParticipantId())
                .timestamp(event.getTimestamp())
                .type(event.getType())
                .afterId(event.getAfterId())
                .beforeId(event.getBeforeId())
                .goldGain(event.getGoldGain())
                .build();
    }

    private SkillEventsEntity toSkillEventsEntity(String matchId, EventsTimeLineDto event) {
        return SkillEventsEntity.builder()
                .matchId(matchId)
                .skillSlot(event.getSkillSlot())
                .participantId(event.getParticipantId())
                .levelUpType(event.getLevelUpType())
                .timestamp(event.getTimestamp())
                .build();
    }

    private KillEventsEntity toKillEventsEntity(String matchId, EventsTimeLineDto event) {
        return KillEventsEntity.builder()
                .matchId(matchId)
                .killerId(event.getKillerId())
                .victimId(event.getVictimId())
                .assistingParticipantIds(convertAssistingIds(event.getAssistingParticipantIds()))
                .bounty(event.getBounty())
                .shutdownBounty(0)
                .killStreakLength(event.getKillStreakLength())
                .position(event.getPosition() != null
                        ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                        : new PositionValue())
                .victimDamageDealt(null)
                .victimDamageReceived(null)
                .timestamp(event.getTimestamp())
                .build();
    }

    private BuildingEventsEntity toBuildingEventsEntity(String matchId, EventsTimeLineDto event) {
        return BuildingEventsEntity.builder()
                .matchId(matchId)
                .assistingParticipantIds(convertAssistingIds(event.getAssistingParticipantIds()))
                .bounty(event.getBounty())
                .buildingType(event.getBuildingType())
                .killerId(event.getKillerId())
                .laneType(event.getLaneType())
                .position(event.getPosition() != null
                        ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                        : new PositionValue())
                .teamId(event.getTeamId())
                .timestamp(event.getTimestamp())
                .towerType(event.getTowerType())
                .type(event.getType())
                .build();
    }

    private WardEventsEntity toWardEventsEntity(String matchId, EventsTimeLineDto event) {
        return WardEventsEntity.builder()
                .matchId(matchId)
                .creatorId(event.getCreatorId() != 0 ? event.getCreatorId() : event.getParticipantId())
                .wardType(event.getWardType())
                .timestamp(event.getTimestamp())
                .build();
    }

    private GameEventsEntity toGameEventsEntity(String matchId, EventsTimeLineDto event, TimelineDto timelineDto) {
        return GameEventsEntity.builder()
                .matchId(matchId)
                .timestamp(event.getTimestamp())
                .gameId(timelineDto.getInfo().getGameId())
                .realTimestamp(event.getRealTimestamp())
                .winningTeam(event.getWinningTeam())
                .build();
    }

    private LevelEventsEntity toLevelEventsEntity(String matchId, EventsTimeLineDto event) {
        return LevelEventsEntity.builder()
                .matchId(matchId)
                .level(event.getLevel())
                .participantId(event.getParticipantId())
                .timestamp(event.getTimestamp())
                .build();
    }

    private ChampionSpecialKillEventEntity toChampionSpecialKillEventEntity(String matchId, EventsTimeLineDto event) {
        return ChampionSpecialKillEventEntity.builder()
                .matchId(matchId)
                .killType(event.getKillType())
                .killerId(event.getKillerId())
                .multiKillLength(event.getMultiKillLength())
                .position(event.getPosition() != null
                        ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                        : new PositionValue())
                .timestamp(event.getTimestamp())
                .build();
    }

    private TurretPlateDestroyedEventEntity toTurretPlateDestroyedEventEntity(String matchId, EventsTimeLineDto event) {
        return TurretPlateDestroyedEventEntity.builder()
                .matchId(matchId)
                .killerId(event.getKillerId())
                .laneType(event.getLaneType())
                .position(event.getPosition() != null
                        ? PositionValue.builder().x(event.getPosition().getX()).y(event.getPosition().getY()).build()
                        : new PositionValue())
                .teamId(event.getTeamId())
                .timestamp(event.getTimestamp())
                .type(event.getType())
                .build();
    }

    private String convertAssistingIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
