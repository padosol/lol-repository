package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchFindService;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class MatchFindListenerTest {

    private final MatchFindService service = mock(MatchFindService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MatchFindListener listener = new MatchFindListener(service, meters);
    private final SummonerRenewalMessage message = new SummonerRenewalMessage("p1", "KR", 0L, 0);

    @Test
    @DisplayName("Riot 5xx 같은 실패는 예외 없이 버리고 reason 별로 센다")
    void findMatchIds_riotFailure_droppedAndCounted() {
        // given
        doThrow(new CompletionException(new RiotServerException(HttpStatus.BAD_GATEWAY, "x")))
                .when(service).process(any());

        // when // then
        assertThatCode(() -> listener.findMatchIdsListener(message)).doesNotThrowAnyException();
        assertThat(meters.get("lol.match.find.dropped").tag("reason", "riot_5xx").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("허가 거절은 다시 던져 재전달되게 한다")
    void findMatchIds_permitDenied_propagates() {
        // given
        doThrow(new RiotPermitDeniedException("KR", RiotCallPriority.BACKGROUND)).when(service).process(any());

        // when // then
        assertThatThrownBy(() -> listener.findMatchIdsListener(message)).isInstanceOf(RiotPermitDeniedException.class);
        assertThat(meters.find("lol.match.find.dropped").counter()).isNull();
    }

    @Test
    @DisplayName("허가 거절은 CompletionException 에 싸여도 다시 던진다")
    void findMatchIds_wrappedPermitDenied_propagates() {
        // given
        doThrow(new CompletionException(new RiotPermitDeniedException("KR", RiotCallPriority.BACKGROUND)))
                .when(service).process(any());

        // when // then
        assertThatThrownBy(() -> listener.findMatchIdsListener(message)).isInstanceOf(CompletionException.class);
        assertThat(meters.find("lol.match.find.dropped").counter()).isNull();
    }
}
