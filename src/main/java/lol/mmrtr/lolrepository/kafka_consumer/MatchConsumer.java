package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MatchConsumer {

    private final MatchService matchService;

    public MatchConsumer(MatchService matchService) {
        this.matchService = matchService;
    }

//    @Async
//    @KafkaListener(topics = "match", groupId = "group_1", containerFactory = "kafkaListenerContainerFactory")
//    public void listener0(
//            @Payload List<MatchDto> matchDtoList
//    )  {
//        log.info("[{}] match message 도착", matchDtoList.size());
//        matchService.bulkSave(matchDtoList);
//    }
//
//    @Async
//    @KafkaListener(topics = "match", groupId = "group_1", containerFactory = "kafkaListenerContainerFactory")
//    public void listener1(
//            @Payload List<MatchDto> matchDtoList
//    )  {
//        log.info("[{}] match message 도착", matchDtoList.size());
//        matchService.bulkSave(matchDtoList);
//    }
//
//    @Async
//    @KafkaListener(topics = "match", groupId = "group_1", containerFactory = "kafkaListenerContainerFactory")
//    public void listener2(
//            @Payload List<MatchDto> matchDtoList
//    )  {
//        log.info("[{}] match message 도착", matchDtoList.size());
//        matchService.bulkSave(matchDtoList);
//    }

}
