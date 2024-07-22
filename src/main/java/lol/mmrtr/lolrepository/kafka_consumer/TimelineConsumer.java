package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.service.TimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TimelineConsumer {

    private final TimelineService timelineService;

    public TimelineConsumer(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @KafkaListener(topics = "timeline", groupId = "group_1")
    public void listener(
            @Payload TimelineDto message
    ) {
        log.info("timeline message 도착");
        timelineService.save(message);
    }
}
