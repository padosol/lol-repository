package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.service.TimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
            @Payload TimelineDto message
    ) {
        log.info("[{}] timeline message 도착", message.getMetadata().getMatchId());
        timelineService.save(message);
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener1(
            @Payload TimelineDto message
    ) {
        log.info("[{}] timeline message 도착", message.getMetadata().getMatchId());
        timelineService.save(message);
    }

    @Async
    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener2(
            @Payload TimelineDto message
    ) {
        log.info("[{}] timeline message 도착", message.getMetadata().getMatchId());
        timelineService.save(message);
    }

}
