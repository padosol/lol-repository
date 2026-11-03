package com.mmrtr.lol.infra.rabbitmq.adapter;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SummonerCollectionPublishAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final SummonerCollectionPublishAdapter adapter = new SummonerCollectionPublishAdapter(rabbitTemplate);

    @Test
    @DisplayName("사용자 갱신 발행은 renewalId 를 싣고 background=false 로 보낸다")
    void publishForRenewal_sendsRenewalIdToSummonerQueue() {
        adapter.publishForRenewal("p1", "KR", "r1");

        assertThat(sent(1)).singleElement().satisfies(m -> {
            assertThat(m.getPuuid()).isEqualTo("p1");
            assertThat(m.getPlatformId()).isEqualTo("KR");
            assertThat(m.getRenewalId()).isEqualTo("r1");
            assertThat(m.getAttempt()).isZero();
        });
    }

    @Test
    @DisplayName("티어 수집 발행은 매치 찾기 큐로 startOffset 0 메시지를 보내고 소환사 큐로는 보내지 않는다")
    void publishForCollection_sendsToMatchFindQueueWithOffsetZero() {
        // given // when
        adapter.publishForCollection(List.of("p1", "p2"), "KR");

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq(RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange()),
                eq(RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey()),
                captor.capture());
        assertThat(captor.getAllValues()).containsExactly(
                new SummonerRenewalMessage("p1", "KR", 0L, 0),
                new SummonerRenewalMessage("p2", "KR", 0L, 0));
        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMqBinding.SUMMONER.getExchange()), eq(RabbitMqBinding.SUMMONER.getRoutingKey()),
                any(Object.class));
    }

    private List<SummonerMessage> sent(int count) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(count)).convertAndSend(
                eq(RabbitMqBinding.SUMMONER.getExchange()), eq(RabbitMqBinding.SUMMONER.getRoutingKey()),
                captor.capture());
        return captor.getAllValues().stream().map(SummonerMessage.class::cast).toList();
    }
}
