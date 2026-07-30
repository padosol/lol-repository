package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchFindService;
import com.mmrtr.lol.support.aop.TraceLogging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * 갱신 후 추가 매치 검색 요청을 소비한다.
 *
 * <p>주의 — 이 큐({@code renewal.match.find.queue})에는 아직 DLX 가 없다.
 * 기존 큐에 {@code x-dead-letter-exchange} 를 추가하면 {@code PRECONDITION_FAILED} 로
 * 채널이 죽으므로 큐 재생성 절차가 선행되어야 한다.
 * 그때까지 재시도 소진 메시지는 격리되지 않고 폐기되며, 폐기 사실을 로그로 남긴다.
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
            throw ListenerFailurePolicy.translate(e, "매치 검색", summonerRenewalMessage.puuid());
        }
    }
}
