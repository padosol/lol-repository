package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchFindService;
import com.mmrtr.lol.support.aop.TraceLogging;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class MatchFindListener {

    private final MatchFindService matchFindService;
    private final MeterRegistry meterRegistry;

    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_MATCH_FIND,
            containerFactory = "findQueueSimpleRabbitListenerContainerFactory")
    public void findMatchIdsListener(@Payload SummonerRenewalMessage summonerRenewalMessage) {
        try {
            matchFindService.process(summonerRenewalMessage);
        } catch (RuntimeException e) {
            // 한도 예약 실패는 한도 대기 뒤에 던져지므로 다시 받아도 바로 돌지 않는다
            if (isBudgetExceeded(e)) {
                throw e;
            }
            // 그 밖의 실패는 requeue 로 같은 메시지가 큐를 막지 않도록 버린다
            log.warn("Dropping match find message, puuid={}: {}", summonerRenewalMessage.puuid(), e.getMessage());
            meterRegistry.counter("lol.match.find.dropped", "reason", RenewalFailureReason.of(e)).increment();
        }
    }

    private boolean isBudgetExceeded(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RiotBudgetExceededException) {
                return true;
            }
        }
        return false;
    }
}
