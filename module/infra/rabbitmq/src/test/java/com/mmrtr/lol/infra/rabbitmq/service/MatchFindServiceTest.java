package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.rabbitmq.RiotPermitFixtures;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalStepPublisher;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchFindServiceTest {

    private final MatchApiPort port = mock(MatchApiPort.class);
    private final RiotPermitPort permitPort = RiotPermitFixtures.grantingPort();
    private final RenewalStepPublisher stepPublisher = mock(RenewalStepPublisher.class);

    @Test
    @DisplayName("matchId 수집(여러 페이지)은 페이지마다 BACKGROUND 로 id 허가 1건을 받는다")
    void collect_takesBackgroundPermitPerPage() {
        // given
        AtomicInteger pages = new AtomicInteger();
        List<String> fullPage = IntStream.range(0, 100).mapToObj(i -> "KR_" + i).toList();
        when(port.fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any()))
                .thenAnswer(inv -> {
                    // 첫 페이지만 가득 차 두 번째 페이지까지 호출되게 한다
                    return CompletableFuture.completedFuture(pages.incrementAndGet() == 1 ? fullPage : List.of("KR_x"));
                });
        MatchFindService service = new MatchFindService(
                new MatchIdCollector(port, permitPort), mock(MatchIdPublisher.class), stepPublisher, Runnable::run);

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 1L));

        // then
        verify(permitPort, times(2))
                .tryAcquire("KR", RiotCallPriority.BACKGROUND, Map.of(RiotMethod.MATCH_IDS_BY_PUUID, 1));
        verify(port, times(2)).fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("중간 페이지에서 거절되면 모은 id 는 발행하고 거절된 offset 부터 이어서 다시 넣는다")
    void deniedMidPagesPublishesCollectedAndRequeuesFromOffset() {
        // given: startOffset 20, 페이지 100건 — 3번째 페이지(offset 220)에서 거절
        List<String> page = IntStream.range(0, 100).mapToObj(i -> "KR_" + i).toList();
        when(port.fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(page));
        AtomicInteger asked = new AtomicInteger();
        when(permitPort.tryAcquire(anyString(), any(), anyMap())).thenAnswer(inv ->
                asked.incrementAndGet() <= 2 ? RiotPermitFixtures.grantingPort().tryAcquire(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)) : Optional.empty());
        MatchIdPublisher publisher = mock(MatchIdPublisher.class);
        MatchFindService service = new MatchFindService(
                new MatchIdCollector(port, permitPort), publisher, stepPublisher, Runnable::run);

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 5L, 20));

        // then
        verify(port, times(2)).fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any());
        verify(publisher).publishNewMatchIds(argThat(ids -> ids.size() == 200), eq("KR"));
        verify(stepPublisher).requeueMatchFindLater(argThat(m ->
                m.startOffset() == 220 && m.puuid().equals("puuid") && m.platform().equals("KR")));
    }

    @Test
    @DisplayName("첫 페이지부터 거절되면 Riot 을 부르지 않고 같은 offset 으로 다시 넣는다")
    void deniedFirstPage_requeuesSameOffsetWithoutRiotCall() {
        // given
        RiotPermitFixtures.denyAll(permitPort);
        MatchIdPublisher publisher = mock(MatchIdPublisher.class);
        MatchFindService service = new MatchFindService(
                new MatchIdCollector(port, permitPort), publisher, stepPublisher, Runnable::run);

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 1L));

        // then
        verify(port, never()).fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any());
        verify(publisher, never()).publishNewMatchIds(any(), anyString());
        verify(stepPublisher).requeueMatchFindLater(argThat(m -> m.startOffset() == 20));
    }

    @Test
    @DisplayName("startOffset 이 0 이면 0 부터 BACKGROUND 로 수집한다")
    void process_startOffsetZero_collectsFromZero() {
        // given
        MatchIdCollector collector = mock(MatchIdCollector.class);
        when(collector.collectAll(anyString(), anyString(), anyLong(), anyInt(), any(), any())).thenReturn(new MatchIdCollector.Collected(List.of(), OptionalInt.empty()));
        MatchFindService service = new MatchFindService(collector, mock(MatchIdPublisher.class), stepPublisher, Runnable::run);

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 0L, 0));

        // then
        verify(collector).collectAll(eq("puuid"), eq("KR"), anyLong(), eq(0), eq(RiotCallPriority.BACKGROUND), any());
    }
}
