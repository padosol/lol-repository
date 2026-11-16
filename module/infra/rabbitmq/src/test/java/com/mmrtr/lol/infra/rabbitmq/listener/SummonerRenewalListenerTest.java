package com.mmrtr.lol.infra.rabbitmq.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalSummonerStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummonerRenewalListenerTest {

    private final RenewalSummonerStep step = mock(RenewalSummonerStep.class);
    private final SummonerRenewalListener listener = new SummonerRenewalListener(step);

    @Test
    @DisplayName("받은 메시지를 그대로 소환사 단계에 넘긴다")
    void receive_delegatesToStep() {
        // given
        SummonerMessage message = new SummonerMessage("KR", "p1", 0L, "r1", 0);

        // when
        listener.receiveSummonerMessageV2(message);

        // then
        verify(step).run(message);
    }
}
