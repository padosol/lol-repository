package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchFindServiceTest {

    @Test
    @DisplayName("matchId 수집(여러 페이지)의 모든 호출은 BACKGROUND 스코프에서 만들어진다")
    void collect_runsUnderBackground() {
        List<RiotCallPriority> seen = new ArrayList<>();
        List<String> fullPage = IntStream.range(0, 100).mapToObj(i -> "KR_" + i).toList();
        MatchApiPort port = mock(MatchApiPort.class);
        when(port.fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any()))
                .thenAnswer(inv -> {
                    seen.add(RiotCallScope.current());
                    // 첫 페이지만 가득 차 두 번째 페이지까지 호출되게 한다
                    return CompletableFuture.completedFuture(seen.size() == 1 ? fullPage : List.of("KR_x"));
                });
        MatchIdPublisher publisher = mock(MatchIdPublisher.class);
        MatchFindService service = new MatchFindService(new MatchIdCollector(port), publisher, Runnable::run);

        service.process(new SummonerRenewalMessage("puuid", "KR", 1L));

        assertThat(seen).hasSize(2).allMatch(p -> p == RiotCallPriority.BACKGROUND);
    }

    @Test
    @DisplayName("startOffset 이 0 이면 0 부터 수집한다")
    void process_startOffsetZero_collectsFromZero() {
        // given
        MatchIdCollector collector = mock(MatchIdCollector.class);
        when(collector.collectAll(anyString(), anyString(), anyLong(), anyInt(), any())).thenReturn(List.of());
        MatchFindService service = new MatchFindService(collector, mock(MatchIdPublisher.class), Runnable::run);

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 0L, 0));

        // then
        verify(collector).collectAll(eq("puuid"), eq("KR"), anyLong(), eq(0), any());
    }
}
