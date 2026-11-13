package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchFetcher.Fetched;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchFetcher.Result;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchPlan.Item;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class RenewalMatchFetcherTest {

    private static final List<String> TWENTY = IntStream.rangeClosed(1, 20).mapToObj(i -> "m" + i).toList();

    private final List<String> events = new ArrayList<>();
    private final FakePermitPort permitPort = new FakePermitPort(events);
    private final FakeMatchApi matchApi = new FakeMatchApi(events);
    private final Executor executor = Runnable::run;
    private final RenewalMatchFetcher fetcher = new RenewalMatchFetcher(matchApi, permitPort, executor);

    private static List<Item> newMatches(List<String> ids) {
        return RenewalMatchPlan.of(ids, Set.of(), Set.of(), Set.of()).items();
    }

    private static RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), null, HttpStatus.TOO_MANY_REQUESTS, "", null);
    }

    private static RiotServerException serverError() {
        return new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "");
    }

    @Test
    @DisplayName("모두 거절되면 USER 로 20 → 10 → 5 → 3 → 2 → 1 을 묻고 20개를 모두 거절로 돌려준다")
    void permitsRequestedDownToOneWhenDenied() {
        // given
        permitPort.allow = counts -> false;
        List<Item> items = newMatches(TWENTY);

        // when
        Result result = fetcher.fetch("KR", items);

        // then
        assertThat(permitPort.requests).extracting(counts -> counts.get(RiotMethod.MATCH)).containsExactly(20, 10, 5, 3, 2, 1);
        assertThat(permitPort.priorities).containsOnly(RiotCallPriority.USER);
        assertThat(result.denied()).containsExactlyElementsOf(items);
        assertThat(result.fetched()).isEmpty();
        assertThat(matchApi.calls).isEmpty();
    }

    @Test
    @DisplayName("묶음 허가에는 그 묶음에 든 타임라인 대상만 센다")
    void costIncludesOnlyRequestedTimelines() {
        // given: 매치 5건까지만 허가한다
        permitPort.allow = counts -> counts.getOrDefault(RiotMethod.MATCH, 0) <= 5;

        // when
        Result result = fetcher.fetch("KR", newMatches(TWENTY));

        // then
        assertThat(permitPort.requests.get(0)).isEqualTo(Map.of(RiotMethod.MATCH, 20, RiotMethod.MATCH_TIMELINE, 3));
        assertThat(permitPort.requests.get(2)).isEqualTo(Map.of(RiotMethod.MATCH, 5, RiotMethod.MATCH_TIMELINE, 3));
        assertThat(permitPort.requests.subList(3, permitPort.requests.size()))
                .allMatch(counts -> !counts.containsKey(RiotMethod.MATCH_TIMELINE));
        assertThat(matchApi.calls).filteredOn(call -> call.startsWith("timeline:"))
                .containsExactly("timeline:m1", "timeline:m2", "timeline:m3");
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactlyElementsOf(TWENTY);
        assertThat(result.denied()).isEmpty();
        assertThat(result.timelineLater()).isEmpty();
    }

    @Test
    @DisplayName("타임라인만 실패하면 매치는 받은 것으로 두고 타임라인은 나중 타임라인으로 넘긴다")
    void timelineFailureKeepsMatchAndQueuesTimelineLater() {
        // given
        matchApi.timelines.put("m1", CompletableFuture.failedFuture(serverError()));

        // when
        Result result = fetcher.fetch("KR", List.of(new Item("m1", true, true)));

        // then
        assertThat(result.fetched()).containsExactly(new Fetched("m1", matchApi.match("m1"), null));
        assertThat(result.fetched().get(0).match()).isSameAs(matchApi.match("m1"));
        assertThat(result.timelineLater()).containsExactly("m1");
        assertThat(result.userRetry()).isEmpty();
        assertThat(result.clientError()).isEmpty();
    }

    @Test
    @DisplayName("매치가 5xx 면 타임라인을 받았어도 버리고 매치를 사용자 재시도로 넘긴다")
    void matchServerErrorGoesToUserRetry() {
        // given
        matchApi.matches.put("m1", CompletableFuture.failedFuture(serverError()));

        // when
        Result result = fetcher.fetch("KR", List.of(new Item("m1", true, true)));

        // then
        assertThat(result.userRetry()).containsExactly("m1");
        assertThat(result.fetched()).isEmpty();
        assertThat(result.timelineLater()).isEmpty();
        assertThat(result.clientError()).isEmpty();
    }

    @Test
    @DisplayName("매치가 429·IO·시간 초과면 사용자 재시도로 넘기고, 상태 429 인 RiotClientException 도 4xx 로 보지 않는다")
    void matchRateLimitOrIoFailureGoesToUserRetry() {
        // given: m1 은 타임라인이 404 여도 매치 실패가 정한다
        matchApi.matches.put("m1", CompletableFuture.failedFuture(rateLimit()));
        matchApi.timelines.put("m1", CompletableFuture.failedFuture(
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)));
        matchApi.matches.put("m2", CompletableFuture.failedFuture(
                new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "", null)));
        matchApi.matches.put("m3", CompletableFuture.failedFuture(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"))));

        // when
        Result result = fetcher.fetch("KR", newMatches(List.of("m1", "m2", "m3")));

        // then
        assertThat(result.userRetry()).containsExactly("m1", "m2", "m3");
        assertThat(result.clientError()).isEmpty();
        assertThat(result.timelineLater()).isEmpty();
    }

    @Test
    @DisplayName("매치가 4xx(429 제외)면 실패 표시 대상으로 돌려준다")
    void matchClientErrorIsMarked() {
        // given
        matchApi.matches.put("m1", CompletableFuture.failedFuture(
                new RiotClientException(HttpStatus.BAD_REQUEST, "", null)));
        matchApi.matches.put("m2", CompletableFuture.failedFuture(
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)));

        // when
        Result result = fetcher.fetch("KR", newMatches(List.of("m1", "m2")));

        // then
        assertThat(result.clientError()).containsExactly("m1", "m2");
        assertThat(result.userRetry()).isEmpty();
        assertThat(result.fetched()).isEmpty();
    }

    @Test
    @DisplayName("타임라인만 받는 항목은 매치를 부르지 않고 MATCH_TIMELINE 허가만 묻는다")
    void timelineOnlyItemFetchesNoMatch() {
        // when
        Result result = fetcher.fetch("KR", List.of(new Item("m1", false, true)));

        // then
        assertThat(permitPort.requests).containsExactly(Map.of(RiotMethod.MATCH_TIMELINE, 1));
        assertThat(matchApi.calls).containsExactly("timeline:m1");
        assertThat(result.fetched()).containsExactly(new Fetched("m1", null, matchApi.timeline("m1")));
        assertThat(result.timelineLater()).isEmpty();
    }

    @Test
    @DisplayName("타임라인만 받는 항목이 429·5xx·IO 로 실패하거나 본문이 비면 나중 타임라인으로 넘긴다")
    void timelineOnlyFailureGoesToTimelineLater() {
        // given
        matchApi.timelines.put("m1", CompletableFuture.failedFuture(serverError()));
        matchApi.timelines.put("m2", CompletableFuture.failedFuture(rateLimit()));
        matchApi.timelines.put("m3", CompletableFuture.failedFuture(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"))));
        matchApi.timelines.put("m4", CompletableFuture.completedFuture(null));

        // when
        Result result = fetcher.fetch("KR", List.of(new Item("m1", false, true), new Item("m2", false, true),
                new Item("m3", false, true), new Item("m4", false, true)));

        // then
        assertThat(result.timelineLater()).containsExactly("m1", "m2", "m3", "m4");
        assertThat(result.fetched()).isEmpty();
        assertThat(result.clientError()).isEmpty();
        assertThat(result.userRetry()).isEmpty();
    }

    @Test
    @DisplayName("타임라인만 받는 항목이 4xx(429 제외)로 실패하면 나중 타임라인으로 넘기지 않고 버린다")
    void timelineOnlyClientErrorIsDropped() {
        // given
        matchApi.timelines.put("m1", CompletableFuture.failedFuture(
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)));
        matchApi.timelines.put("m2", CompletableFuture.failedFuture(
                new RiotClientException(HttpStatus.BAD_REQUEST, "", null)));

        // when
        Result result = fetcher.fetch("KR", List.of(new Item("m1", false, true), new Item("m2", false, true)));

        // then
        assertThat(result.timelineLater()).isEmpty();
        assertThat(result.fetched()).isEmpty();
        assertThat(result.clientError()).isEmpty();
        assertThat(result.userRetry()).isEmpty();
        assertThat(result.denied()).isEmpty();
    }

    @Test
    @DisplayName("본문이 빈 매치는 사용자 재시도로, 빈 타임라인은 받지 않은 것으로 둔다")
    void nullBodiesAreNotFetched() {
        // given
        matchApi.matches.put("m1", CompletableFuture.completedFuture(null));
        matchApi.timelines.put("m2", CompletableFuture.completedFuture(null));

        // when
        Result result = fetcher.fetch("KR", newMatches(List.of("m1", "m2")));

        // then
        assertThat(result.userRetry()).containsExactly("m1");
        assertThat(result.fetched()).containsExactly(new Fetched("m2", matchApi.match("m2"), null));
        assertThat(result.timelineLater()).containsExactly("m2");
    }

    @Test
    @DisplayName("허가된 묶음은 다음 허가를 묻기 전에 시작하고, 남은 것이 끝내 거절되면 순서대로 돌려준다")
    void startsEachGrantedChunkBeforeAskingNext() {
        // given: 두 번째 요청(2건)만 허가한다
        permitPort.allow = counts -> permitPort.requests.size() == 2;
        List<Item> items = List.of("m1", "m2", "m3", "m4").stream()
                .map(matchId -> new Item(matchId, true, false)).toList();

        // when
        Result result = fetcher.fetch("KR", items);

        // then
        assertThat(events).containsExactly(
                "permit:4", "permit:2", "match:m1", "match:m2", "permit:2", "permit:1");
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactly("m1", "m2");
        assertThat(result.denied()).containsExactly(items.get(2), items.get(3));
    }

    @Test
    @DisplayName("허가를 묻다 예외가 나면 이미 시작한 묶음은 받고, 시작하지 못한 항목은 거절된 것으로 둔다")
    void permitCheckFailureLeavesUnstartedItemsDenied() {
        // given: 4건 거절 → 앞 2건 허가 → 남은 2건을 묻다 예외
        permitPort.allow = counts -> switch (permitPort.requests.size()) {
            case 1 -> false;
            case 2 -> true;
            default -> throw new IllegalStateException("redis down");
        };
        List<Item> more = List.of(new Item("m3", true, false), new Item("m4", false, true));
        List<Item> all = new ArrayList<>(List.of(new Item("m1", true, false), new Item("m2", false, true)));
        all.addAll(more);

        // when
        Result result = fetcher.fetch("KR", all);

        // then
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactly("m1", "m2");
        assertThat(result.denied()).containsExactlyElementsOf(more);
        assertThat(matchApi.calls).containsExactly("match:m1", "timeline:m2");
    }

    /** 요청을 기록하고 allow 가 true 인 요청만 허가하는 가짜 허가 포트. */
    private static final class FakePermitPort implements RiotPermitPort {

        private final List<String> events;
        private final List<Map<RiotMethod, Integer>> requests = new ArrayList<>();
        private final List<RiotCallPriority> priorities = new ArrayList<>();
        private Function<Map<RiotMethod, Integer>, Boolean> allow = counts -> true;

        FakePermitPort(List<String> events) {
            this.events = events;
        }

        @Override
        public Optional<RiotPermits> tryAcquire(String platform, RiotCallPriority priority,
                                                Map<RiotMethod, Integer> counts) {
            requests.add(Map.copyOf(counts));
            priorities.add(priority);
            int size = Math.max(counts.getOrDefault(RiotMethod.MATCH, 0),
                    counts.getOrDefault(RiotMethod.MATCH_TIMELINE, 0));
            events.add("permit:" + size);
            if (!allow.apply(counts)) {
                return Optional.empty();
            }
            List<RiotPermit> permits = new ArrayList<>();
            counts.forEach((method, count) -> IntStream.rangeClosed(1, count).forEach(k ->
                    permits.add(new RiotPermit("t-" + method + "-" + k, platform, method, priority))));
            return Optional.of(RiotPermits.of(permits));
        }
    }

    /** 실제 어댑터처럼 permits 에서 그 method 의 허가를 하나 꺼내고 부른 것을 기록하는 가짜 매치 API. */
    private static final class FakeMatchApi implements MatchApiPort {

        private final List<String> events;
        private final List<String> calls = new ArrayList<>();
        private final Map<String, MatchDto> matchDtos = new HashMap<>();
        private final Map<String, TimelineDto> timelineDtos = new HashMap<>();
        private final Map<String, CompletableFuture<MatchDto>> matches = new HashMap<>();
        private final Map<String, CompletableFuture<TimelineDto>> timelines = new HashMap<>();

        FakeMatchApi(List<String> events) {
            this.events = events;
        }

        MatchDto match(String matchId) {
            return matchDtos.computeIfAbsent(matchId, id -> new MatchDto());
        }

        TimelineDto timeline(String matchId) {
            return timelineDtos.computeIfAbsent(matchId, id -> new TimelineDto());
        }

        @Override
        public CompletableFuture<List<String>> fetchMatchIdsByPuuid(String puuid, String platform, long startTime,
                                                                    int start, int count, RiotPermits permits,
                                                                    Executor executor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<MatchDto> fetchMatchById(String matchId, String platform, RiotPermits permits,
                                                          Executor executor) {
            permits.take(RiotMethod.MATCH);
            calls.add("match:" + matchId);
            events.add("match:" + matchId);
            return matches.getOrDefault(matchId, CompletableFuture.completedFuture(match(matchId)));
        }

        @Override
        public CompletableFuture<TimelineDto> fetchTimelineById(String matchId, String platform,
                                                                RiotPermits permits, Executor executor) {
            permits.take(RiotMethod.MATCH_TIMELINE);
            calls.add("timeline:" + matchId);
            events.add("timeline:" + matchId);
            return timelines.getOrDefault(matchId, CompletableFuture.completedFuture(timeline(matchId)));
        }
    }
}
