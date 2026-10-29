package com.mmrtr.lol.infra.rabbitmq.adapter;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SummonerCollectionPublishAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final SummonerCollectionPublishAdapter adapter = new SummonerCollectionPublishAdapter(rabbitTemplate);

    @Test
    @DisplayName("사용자 갱신 발행은 background=false 로 보낸다")
    void publishForRenewal_sendsUserMessage() {
        adapter.publishForRenewal(List.of("p1"), "KR");

        assertThat(sent(1)).singleElement().satisfies(m -> {
            assertThat(m.getPuuid()).isEqualTo("p1");
            assertThat(m.getPlatformId()).isEqualTo("KR");
            assertThat(m.isBackground()).isFalse();
        });
    }

    @Test
    @DisplayName("티어 수집 발행은 background=true 로 보낸다")
    void publishForCollection_sendsBackgroundMessage() {
        adapter.publishForCollection(List.of("p1", "p2"), "KR");

        assertThat(sent(2)).extracting(SummonerMessage::getPuuid).containsExactly("p1", "p2");
        assertThat(sent(2)).allMatch(SummonerMessage::isBackground);
    }

    private List<SummonerMessage> sent(int count) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(count)).convertAndSend(
                eq(RabbitMqBinding.SUMMONER.getExchange()), eq(RabbitMqBinding.SUMMONER.getRoutingKey()),
                captor.capture());
        return captor.getAllValues().stream().map(SummonerMessage.class::cast).toList();
    }
}
