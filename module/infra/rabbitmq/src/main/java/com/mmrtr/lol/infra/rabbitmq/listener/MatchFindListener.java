package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchFindService;
import com.mmrtr.lol.support.aop.TraceLogging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * 전적 갱신에서 최근 20게임을 넘는 매치가 있을 때 발행되는 추가 매치 검색 요청을 소비한다.
 * 검색된 matchId 는 {@code mmrtr.matchId} 큐로 발행되어 {@link MatchListener} 가 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class MatchFindListener {

    private final MatchFindService matchFindService;

    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_MATCH_FIND,
            containerFactory = "findQueueSimpleRabbitListenerContainerFactory")
    public void findMatchIdsListener(@Payload SummonerRenewalMessage summonerRenewalMessage) {
        try {
            matchFindService.process(summonerRenewalMessage);
        } catch (Exception e) {
            // 예외를 그대로 던지면 컨테이너가 requeue 해 같은 메시지를 무한 재전달한다.
            // 이 큐에는 DLX 가 없으므로 reject 된 메시지는 폐기된다.
            log.error("추가 매치 검색 실패. 메시지를 폐기합니다. message={}", summonerRenewalMessage, e);
            throw new AmqpRejectAndDontRequeueException("추가 매치 검색 실패", e);
        }
    }
}
