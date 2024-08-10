package lol.mmrtr.lolrepository.service;

import lol.mmrtr.lolrepository.entity.ParticipantFrame;
import lol.mmrtr.lolrepository.entity.TimeLineEvent;
import lol.mmrtr.lolrepository.entity.event.*;
import lol.mmrtr.lolrepository.repository.EventVictimDamageDealtRepository;
import lol.mmrtr.lolrepository.repository.EventVictimDamageReceivedRepository;
import lol.mmrtr.lolrepository.repository.ParticipantFrameRepository;
import lol.mmrtr.lolrepository.repository.TimeLineEventRepository;
import lol.mmrtr.lolrepository.repository.event.ItemEventRepository;
import lol.mmrtr.lolrepository.repository.event.SkillEventRepository;
import lol.mmrtr.lolrepository.riot.dto.match_timeline.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TimelineService {

    private final TimeLineEventRepository timeLineEventRepository;
    private final ParticipantFrameRepository participantFrameRepository;

    private final EventVictimDamageDealtRepository eventVictimDamageDealtRepository;
    private final EventVictimDamageReceivedRepository eventVictimDamageReceivedRepository;

    // events
    private final ItemEventRepository itemEventRepository;
    private final SkillEventRepository skillEventRepository;

    @Transactional
    public void save(TimelineDto timelineDto) {

        String matchId = timelineDto.getMetadata().getMatchId();

        Map<Integer, String> puuidMap = new HashMap<>();
        List<ParticipantTimeLineDto> participants = timelineDto.getInfo().getParticipants();
        for (ParticipantTimeLineDto participant : participants) {
            puuidMap.put(participant.getParticipantId(), participant.getPuuid());
        }

        List<FramesTimeLineDto> frames = timelineDto.getInfo().getFrames();

        //
        List<TimeLineEvent> timeLineEvents = new ArrayList<>();
        List<ParticipantFrame> participantFramesData = new ArrayList<>();

        // log event
        List<BuildingEvents> buildingEvents = new ArrayList<>();
        List<ChampionSpecialKillEvent> championSpecialKillEvents = new ArrayList<>();
        List<GameEvents> gameEvents = new ArrayList<>();
        List<ItemEvents> itemEvents = new ArrayList<>();
        List<KillEvents> killEvents = new ArrayList<>();
        List<LevelEvents> levelEvents = new ArrayList<>();
        List<SkillEvents> skillEvents = new ArrayList<>();
        List<TurretPlateDestroyedEvent> turretPlateDestroyedEvents = new ArrayList<>();
        List<WardEvents> wardEvents = new ArrayList<>();

        for (FramesTimeLineDto frame : frames) {

            int timestamp = frame.getTimestamp();

            timeLineEvents.add(new TimeLineEvent(matchId, timestamp));

            // timeline event
            List<EventsTimeLineDto> events = frame.getEvents();
            for (EventsTimeLineDto event : events) {

                switch (event.getType()) {
                    case "ITEM_PURCHASED", "ITEM_UNDO", "ITEM_DESTROYED", "ITEM_SOLD" -> {
                        itemEvents.add(new ItemEvents(matchId, timestamp, event));
                    }
                    case "SKILL_LEVEL_UP" -> {
                        skillEvents.add(new SkillEvents(matchId, timestamp, event));
                    }
                    case "LEVEL_UP" -> {
                        levelEvents.add(new LevelEvents(timestamp, event));
                    }
                    case "WARD_PLACED", "WARD_KILL" -> {
                        wardEvents.add(new WardEvents(timestamp, event));
                    }
                    case "CHAMPION_SPECIAL_KILL" -> {
                        championSpecialKillEvents.add(new ChampionSpecialKillEvent(timestamp, event));
                    }
                    case "CHAMPION_KILL" -> {
                        killEvents.add(new KillEvents(timestamp, event));
                    }
                    case "TURRET_PLATE_DESTROYED" -> {
                        turretPlateDestroyedEvents.add(new TurretPlateDestroyedEvent(timestamp, event));
                    }
                    case "BUILDING_KILL" -> {
                        buildingEvents.add(new BuildingEvents(timestamp, event));
                    }
                    case "PAUSE_END", "GAME_END" -> {
                        gameEvents.add(new GameEvents(timestamp, event));
                    }
                };
            }

            // participant frame
            ParticipantFramesDto participantFrames = frame.getParticipantFrames();

            List<ParticipantFrameDto> list = participantFrames.getList();
            for (ParticipantFrameDto participantFrameDto : list) {

                if(participantFrameDto == null) continue;

                ParticipantFrame participantFrame = new ParticipantFrame(
                        matchId,
                        timestamp,
                        participantFrameDto
                );

                participantFramesData.add(participantFrame);
            }
        }

        timeLineEventRepository.bulkSave(timeLineEvents);
        participantFrameRepository.bulkSave(participantFramesData);

        itemEventRepository.bulkSave(itemEvents);
        skillEventRepository.bulkSave(skillEvents);
    }

    @Transactional
    public void bulkSave(List<TimelineDto> timelineDtoList) {
        //
        List<TimeLineEvent> timeLineEvents = new ArrayList<>();
        List<ParticipantFrame> participantFramesData = new ArrayList<>();

        // log event
        List<BuildingEvents> buildingEvents = new ArrayList<>();
        List<ChampionSpecialKillEvent> championSpecialKillEvents = new ArrayList<>();
        List<GameEvents> gameEvents = new ArrayList<>();
        List<ItemEvents> itemEvents = new ArrayList<>();
        List<KillEvents> killEvents = new ArrayList<>();
        List<LevelEvents> levelEvents = new ArrayList<>();
        List<SkillEvents> skillEvents = new ArrayList<>();
        List<TurretPlateDestroyedEvent> turretPlateDestroyedEvents = new ArrayList<>();
        List<WardEvents> wardEvents = new ArrayList<>();


        for (TimelineDto timelineDto : timelineDtoList) {

            String matchId = timelineDto.getMetadata().getMatchId();

            Map<Integer, String> puuidMap = new HashMap<>();
            List<ParticipantTimeLineDto> participants = timelineDto.getInfo().getParticipants();
            for (ParticipantTimeLineDto participant : participants) {
                puuidMap.put(participant.getParticipantId(), participant.getPuuid());
            }

            List<FramesTimeLineDto> frames = timelineDto.getInfo().getFrames();

            for (FramesTimeLineDto frame : frames) {

                int timestamp = frame.getTimestamp();

                timeLineEvents.add(new TimeLineEvent(matchId, timestamp));

                // timeline event
                List<EventsTimeLineDto> events = frame.getEvents();
                for (EventsTimeLineDto event : events) {

                    switch (event.getType()) {
                        case "ITEM_PURCHASED", "ITEM_UNDO", "ITEM_DESTROYED", "ITEM_SOLD" -> {
                            itemEvents.add(new ItemEvents(matchId, timestamp, event));
                        }
                        case "SKILL_LEVEL_UP" -> {
                            skillEvents.add(new SkillEvents(matchId, timestamp, event));
                        }
                        case "LEVEL_UP" -> {
                            levelEvents.add(new LevelEvents(timestamp, event));
                        }
                        case "WARD_PLACED", "WARD_KILL" -> {
                            wardEvents.add(new WardEvents(timestamp, event));
                        }
                        case "CHAMPION_SPECIAL_KILL" -> {
                            championSpecialKillEvents.add(new ChampionSpecialKillEvent(timestamp, event));
                        }
                        case "CHAMPION_KILL" -> {
                            killEvents.add(new KillEvents(timestamp, event));
                        }
                        case "TURRET_PLATE_DESTROYED" -> {
                            turretPlateDestroyedEvents.add(new TurretPlateDestroyedEvent(timestamp, event));
                        }
                        case "BUILDING_KILL" -> {
                            buildingEvents.add(new BuildingEvents(timestamp, event));
                        }
                        case "PAUSE_END", "GAME_END" -> {
                            gameEvents.add(new GameEvents(timestamp, event));
                        }
                    };
                }

                // participant frame
                ParticipantFramesDto participantFrames = frame.getParticipantFrames();

                if(participantFrames != null) {
                    List<ParticipantFrameDto> list = participantFrames.getList();
                    for (ParticipantFrameDto participantFrameDto : list) {

                        if(participantFrameDto == null) continue;

                        ParticipantFrame participantFrame = new ParticipantFrame(
                                matchId,
                                timestamp,
                                participantFrameDto
                        );

                        participantFramesData.add(participantFrame);
                    }
                }

            }

        }


        timeLineEventRepository.bulkSave(timeLineEvents);
        participantFrameRepository.bulkSave(participantFramesData);

        itemEventRepository.bulkSave(itemEvents);
        skillEventRepository.bulkSave(skillEvents);
    }
}
