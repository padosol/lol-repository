package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.rabbitmq.RiotPermitFixtures;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalStepPublisher;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private final MatchIdPublisher publisher = mock(MatchIdPublisher.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<String> page = IntStream.range(0, 100).mapToObj(i -> "KR_" + i).toList();

    private MatchFindService service(MatchIdCollector collector) {
        return new MatchFindService(collector, publisher, stepPublisher, Runnable::run, meters);
    }

    /** 첫 페이지(가득 참)는 받고 두 번째 페이지는 failure 로 실패한다. */
    private void givenSecondPageFails(RuntimeException failure) {
        AtomicInteger pages = new AtomicInteger();
        when(port.fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any()))
                .thenAnswer(inv -> pages.incrementAndGet() == 1
                        ? CompletableFuture.completedFuture(page) : CompletableFuture.failedFuture(failure));
    }

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
        MatchFindService service = service(new MatchIdCollector(port, permitPort));

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 1L));

        // then
        verify(permitPort, times(2))
                .tryAcquire("KR", RiotCallPriority.BACKGROUND, Map.of(RiotMethod.MATCH_IDS_BY_PUUID, 1));
        verify(port, times(2)).fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("중간 페이지에서 거절되면 모은 id 는 발행하고 거절된 offset 부터 시도 수를 그대로 둔 채 이어서 다시 넣는다")
    void deniedMidPagesPublishesCollectedAndRequeuesFromOffset() {
        // given: startOffset 20, 페이지 100건 — 3번째 페이지(offset 220)에서 거절
        when(port.fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(page));
        AtomicInteger asked = new AtomicInteger();
        when(permitPort.tryAcquire(anyString(), any(), anyMap())).thenAnswer(inv ->
                asked.incrementAndGet() <= 2 ? RiotPermitFixtures.grantingPort().tryAcquire(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)) : Optional.empty());
        MatchFindService service = service(new MatchIdCollector(port, permitPort));

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 5L, 20, 1));

        // then
        verify(port, times(2)).fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any());
        verify(publisher).publishNewMatchIds(argThat(ids -> ids.size() == 200), eq("KR"));
        verify(stepPublisher).requeueMatchFindLater(new SummonerRenewalMessage("puuid", "KR", 5L, 220, 1));
    }

    @Test
    @DisplayName("중간 페이지가 일시 오류면 모은 id 는 발행하고 그 offset 부터 시도 수를 하나 올려 잠시 뒤 다시 넣는다")
    void transientMidPagesPublishesCollectedAndRequeuesWithNextAttempt() {
        // given: startOffset 20 — 두 번째 페이지(offset 120)가 5xx
        givenSecondPageFails(new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "x"));
        MatchFindService service = service(new MatchIdCollector(port, permitPort));

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 5L, 20));

        // then
        verify(publisher).publishNewMatchIds(page, "KR");
        verify(stepPublisher).requeueMatchFindLater(new SummonerRenewalMessage("puuid", "KR", 5L, 120, 1));
        assertThat(meters.find("lol.match.find.dropped").counter()).isNull();
    }

    @Test
    @DisplayName("일시 오류인데 다음 시도 수가 상한(2)에 닿으면 모은 id 만 발행하고 다시 넣지 않으며 dropped 로 센다")
    void transientAtMaxAttemptPublishesCollectedAndDrops() {
        // given
        givenSecondPageFails(new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "x"));
        MatchFindService service = service(new MatchIdCollector(port, permitPort));

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 5L, 20, 1));

        // then
        verify(publisher).publishNewMatchIds(page, "KR");
        verify(stepPublisher, never()).requeueMatchFindLater(any());
        assertThat(meters.get("lol.match.find.dropped").tag("reason", "riot_5xx").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("4xx 면 지금처럼 메시지째 버리도록 던지고, 모은 id 도 발행하지 않는다")
    void clientErrorPropagatesWithoutPublishing() {
        // given
        givenSecondPageFails(new RiotClientException(HttpStatus.BAD_REQUEST, "x", null));
        MatchFindService service = service(new MatchIdCollector(port, permitPort));

        // when // then
        assertThatThrownBy(() -> service.process(new SummonerRenewalMessage("puuid", "KR", 5L, 20)))
                .hasRootCauseInstanceOf(RiotClientException.class);
        verify(publisher, never()).publishNewMatchIds(any(), anyString());
        verify(stepPublisher, never()).requeueMatchFindLater(any());
    }

    @Test
    @DisplayName("첫 페이지부터 거절되면 Riot 을 부르지 않고 같은 offset 으로 다시 넣는다")
    void deniedFirstPage_requeuesSameOffsetWithoutRiotCall() {
        // given
        RiotPermitFixtures.denyAll(permitPort);
        MatchFindService service = service(new MatchIdCollector(port, permitPort));

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 1L));

        // then
        verify(port, never()).fetchMatchIdsByPuuid(anyString(), anyString(), anyLong(), anyInt(), anyInt(), any(), any());
        verify(publisher, never()).publishNewMatchIds(any(), anyString());
        verify(stepPublisher).requeueMatchFindLater(argThat(m -> m.startOffset() == 20 && m.attempt() == 0));
    }

    @Test
    @DisplayName("startOffset 이 0 이면 0 부터 BACKGROUND 로 수집한다")
    void process_startOffsetZero_collectsFromZero() {
        // given
        MatchIdCollector collector = mock(MatchIdCollector.class);
        when(collector.collectAll(anyString(), anyString(), anyLong(), anyInt(), any(), any()))
                .thenReturn(new MatchIdCollector.Collected(List.of(), Optional.empty()));
        MatchFindService service = service(collector);

        // when
        service.process(new SummonerRenewalMessage("puuid", "KR", 0L, 0));

        // then
        verify(collector).collectAll(eq("puuid"), eq("KR"), anyLong(), eq(0), eq(RiotCallPriority.BACKGROUND), any());
    }
}
