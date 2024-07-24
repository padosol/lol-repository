package lol.mmrtr.lolrepository.service;

import lol.mmrtr.lolrepository.dto.match_timeline.*;
import lol.mmrtr.lolrepository.entity.ParticipantFrame;
import lol.mmrtr.lolrepository.entity.TimeLineEvent;
import lol.mmrtr.lolrepository.entity.event.*;
import lol.mmrtr.lolrepository.repository.EventVictimDamageDealtRepository;
import lol.mmrtr.lolrepository.repository.EventVictimDamageReceivedRepository;
import lol.mmrtr.lolrepository.repository.ParticipantFrameRepository;
import lol.mmrtr.lolrepository.repository.TimeLineEventRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TimelineService {

    private final TimeLineEventRepository timeLineEventRepository;
    private final ParticipantFrameRepository participantFrameRepository;
    private final EventVictimDamageDealtRepository eventVictimDamageDealtRepository;
    private final EventVictimDamageReceivedRepository eventVictimDamageReceivedRepository;

    public TimelineService(
        TimeLineEventRepository timeLineEventRepository,
        ParticipantFrameRepository participantFrameRepository,
        EventVictimDamageDealtRepository eventVictimDamageDealtRepository,
        EventVictimDamageReceivedRepository eventVictimDamageReceivedRepository
    ) {
        this.timeLineEventRepository = timeLineEventRepository;
        this.participantFrameRepository = participantFrameRepository;
        this.eventVictimDamageDealtRepository = eventVictimDamageDealtRepository;
        this.eventVictimDamageReceivedRepository = eventVictimDamageReceivedRepository;
    }

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

            // timeline event
            List<EventsTimeLineDto> events = frame.getEvents();
            for (EventsTimeLineDto event : events) {
                
            }

            // participant frame
            ParticipantFramesDto participantFrames = frame.getParticipantFrames();

            List<ParticipantFrameDto> list = participantFrames.getList();
            for (ParticipantFrameDto participantFrameDto : list) {
                int participantId = participantFrameDto.getParticipantId();

                ParticipantFrame participantFrame = new ParticipantFrame(
                        matchId,
                        timestamp,
                        puuidMap.get(participantId),
                        participantFrameDto
                );

                participantFramesData.add(participantFrame);
            }
        }

        timeLineEventRepository.bulkSave(timeLineEvents);
        participantFrameRepository.bulkSave(participantFramesData);

    }



}
