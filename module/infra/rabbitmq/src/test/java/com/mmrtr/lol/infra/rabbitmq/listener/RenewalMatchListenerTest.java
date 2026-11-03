package com.mmrtr.lol.infra.rabbitmq.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalMatchListenerTest {

    private final RenewalMatchStep step = mock(RenewalMatchStep.class);
    private final RenewalMatchListener listener = new RenewalMatchListener(step);

    @Test
    @DisplayName("받은 메시지와 재전달 여부를 그대로 매치 단계에 넘긴다")
    void receive_delegatesWithRedeliveredFlag() {
        // given
        RenewalStepMessage message = new RenewalStepMessage("r1", "p1", "KR", 100L, 0);

        // when
        listener.receive(message, true);

        // then
        verify(step).run(message, true);
    }
}
