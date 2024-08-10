package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.service.TimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TimelineConsumer {

    private final TimelineService timelineService;

    public TimelineConsumer(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener0(
            @Payload List<TimelineDto> message
    ) {
        log.info("[{}] timeline message 도착", message.size());
        timelineService.bulkSave(message);
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener1(
            @Payload List<TimelineDto> message
    ) {
        log.info("[{}] timeline message 도착", message.size());
        timelineService.bulkSave(message);
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener2(
            @Payload List<TimelineDto> message
    ) {
        log.info("[{}] timeline message 도착", message.size());
        timelineService.bulkSave(message);
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener3(
            @Payload List<TimelineDto> message
    ) {
        log.info("[{}] timeline message 도착", message.size());
        timelineService.bulkSave(message);
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener4(
            @Payload List<TimelineDto> message
    ) {
        log.info("[{}] timeline message 도착", message.size());
        timelineService.bulkSave(message);
    }

}
