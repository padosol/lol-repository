package com.mmrtr.lol.infra.persistence.match.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.readmodel.timeline.EventsTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.FramesTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.TimelineEventFrameEntity;
import com.mmrtr.lol.infra.persistence.match.repository.TimelineEventJsonRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class TimelineEventJsonService {

    private final TimelineEventJsonRepositoryImpl repository;
    private final ObjectMapper eventMapper;

    public TimelineEventJsonService(TimelineEventJsonRepositoryImpl repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.eventMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void saveAll(List<TimelineDto> timelineDtos) {
        if (timelineDtos == null || timelineDtos.isEmpty()) return;

        long start = System.currentTimeMillis();

        List<String> matchIds = timelineDtos.stream()
                .filter(dto -> dto != null && dto.getMetadata() != null)
                .map(dto -> dto.getMetadata().getMatchId())
                .toList();
        Set<String> existingMatchIds = repository.findExistingMatchIds(matchIds);

        List<TimelineEventFrameEntity> allEvents = new ArrayList<>();
        for (TimelineDto dto : timelineDtos) {
            if (dto == null || dto.getMetadata() == null || dto.getInfo() == null) continue;
            String matchId = dto.getMetadata().getMatchId();
            if (existingMatchIds.contains(matchId)) {
                log.debug("[timeline-json] matchId {} 이미 존재, 스킵", matchId);
                continue;
            }

            List<FramesTimeLineDto> frames = dto.getInfo().getFrames();
            if (frames == null || frames.isEmpty()) continue;

            for (FramesTimeLineDto frame : frames) {
                List<EventsTimeLineDto> events = frame.getEvents();
                if (events == null || events.isEmpty()) continue;

                long frameTs = frame.getTimestamp();
                for (int i = 0; i < events.size(); i++) {
                    TimelineEventFrameEntity entity = toEventFrameEntity(matchId, frameTs, i, events.get(i));
                    if (entity != null) allEvents.add(entity);
                }
            }
        }

        repository.bulkSaveTimelineEventFrames(allEvents);

        log.debug("[timeline-json] 총 소요: {}ms, 저장 {}건", System.currentTimeMillis() - start, allEvents.size());
    }

    private TimelineEventFrameEntity toEventFrameEntity(String matchId, long frameTs, int eventIndex, EventsTimeLineDto event) {
        if (event == null || event.getType() == null) return null;
        try {
            return TimelineEventFrameEntity.builder()
                    .matchId(matchId)
                    .timestamp(frameTs)
                    .eventIndex(eventIndex)
                    .data(eventMapper.writeValueAsString(event))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize timeline event", e);
        }
    }
}
