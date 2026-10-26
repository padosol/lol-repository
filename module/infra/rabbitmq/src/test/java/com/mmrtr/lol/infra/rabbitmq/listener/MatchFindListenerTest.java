package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchFindService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchFindListenerTest {

    private static final SummonerRenewalMessage MESSAGE =
            new SummonerRenewalMessage("puuid-1", "KR", 1767225600L);

    private MatchFindService matchFindService;
    private MatchFindListener listener;

    @BeforeEach
    void setUp() {
        matchFindService = mock(MatchFindService.class);
        listener = new MatchFindListener(matchFindService);
    }

    @Test
    @DisplayName("추가 매치 검색 요청을 MatchFindService 로 위임한다")
    void findMatchIdsListener_delegatesToService() {
        listener.findMatchIdsListener(MESSAGE);

        verify(matchFindService).process(MESSAGE);
    }

    @Test
    @DisplayName("검색이 실패하면 재전달 루프에 빠지지 않도록 requeue 없이 reject 한다")
    void findMatchIdsListener_rejectsWithoutRequeue_whenServiceThrows() {
        doThrow(new IllegalStateException("riot api down")).when(matchFindService).process(MESSAGE);

        assertThatThrownBy(() -> listener.findMatchIdsListener(MESSAGE))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
