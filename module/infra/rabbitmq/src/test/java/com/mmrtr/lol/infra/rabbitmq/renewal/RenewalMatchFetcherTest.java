package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchFetcher.Fetched;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchFetcher.Result;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchPlan.Item;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RenewalMatchFetcherTest {

    private static final List<String> TWENTY = IntStream.rangeClosed(1, 20).mapToObj(i -> "m" + i).toList();
    private static final Predicate<String> CLAIM_ALL = matchId -> true;

    private final List<String> events = new ArrayList<>();
    private final FakePermitPort permitPort = new FakePermitPort(events);
    private final FakeMatchApi matchApi = new FakeMatchApi(events);
    private final Executor executor = Runnable::run;
    private final RenewalMatchFetcher fetcher = new RenewalMatchFetcher(matchApi, permitPort, executor);

    private static List<Item> items(List<String> ids) {
        return RenewalMatchPlan.items(ids);
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
        List<Item> items = items(TWENTY);

        // when
        Result result = fetcher.fetch("KR", items, CLAIM_ALL);

        // then
        assertThat(permitPort.requests).extracting(counts -> counts.get(RiotMethod.MATCH))
                .containsExactly(20, 10, 5, 3, 2, 1);
        assertThat(permitPort.priorities).containsOnly(RiotCallPriority.USER);
        assertThat(result.denied()).containsExactlyElementsOf(items);
        assertThat(result.fetched()).isEmpty();
        assertThat(matchApi.calls).isEmpty();
    }

    @Test
    @DisplayName("앞 3개만 타임라인까지 허가를 묻고 부른다")
    void costCountsTimelineOnlyForFirstThree() {
        // when
        Result result = fetcher.fetch("KR", items(List.of("m1", "m2", "m3", "m4", "m5")), CLAIM_ALL);

        // then
        assertThat(permitPort.requests).containsExactly(Map.of(RiotMethod.MATCH, 5, RiotMethod.MATCH_TIMELINE, 3));
        assertThat(matchApi.calls).filteredOn(call -> call.startsWith("timeline:"))
                .containsExactly("timeline:m1", "timeline:m2", "timeline:m3");
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactly("m1", "m2", "m3", "m4", "m5");
        assertThat(result.fetched()).filteredOn(fetched -> fetched.timeline() == null)
                .extracting(Fetched::matchId).containsExactly("m4", "m5");
    }

    @Test
    @DisplayName("허가 뒤 선점에 지면 부르지 않고 claimedElsewhere 로 돌려준다")
    void claimLostAfterPermit_notCalled() {
        // when
        Result result = fetcher.fetch("KR", items(List.of("m1", "m2", "m3")), matchId -> !matchId.equals("m2"));

        // then
        assertThat(matchApi.calls).noneMatch(call -> call.endsWith(":m2"));
        assertThat(result.claimedElsewhere()).containsExactly("m2");
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactly("m1", "m3");
        assertThat(result.denied()).isEmpty();
    }

    @Test
    @DisplayName("매치가 어떤 이유로든 실패하면 failed 로 돌려준다")
    void matchFailure_anyCause_isFailed() {
        // given
        matchApi.matches.put("m1", CompletableFuture.failedFuture(serverError()));
        matchApi.matches.put("m2", CompletableFuture.failedFuture(rateLimit()));
        matchApi.matches.put("m3", CompletableFuture.failedFuture(
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)));

        // when
        Result result = fetcher.fetch("KR", items(List.of("m1", "m2", "m3")), CLAIM_ALL);

        // then
        assertThat(result.failed()).containsExactly("m1", "m2", "m3");
        assertThat(result.fetched()).isEmpty();
    }

    @Test
    @DisplayName("타임라인만 실패하면 매치는 받은 것으로 두고 타임라인은 비운다")
    void timelineFailure_keepsMatchWithoutTimeline() {
        // given
        matchApi.timelines.put("m1", CompletableFuture.failedFuture(serverError()));

        // when
        Result result = fetcher.fetch("KR", items(List.of("m1")), CLAIM_ALL);

        // then
        assertThat(result.fetched()).containsExactly(new Fetched("m1", matchApi.match("m1"), null));
        assertThat(result.failed()).isEmpty();
    }

    @Test
    @DisplayName("본문이 빈 매치는 failed, 빈 타임라인은 받지 않은 것으로 둔다")
    void nullBodies() {
        // given
        matchApi.matches.put("m1", CompletableFuture.completedFuture(null));
        matchApi.timelines.put("m2", CompletableFuture.completedFuture(null));

        // when
        Result result = fetcher.fetch("KR", items(List.of("m1", "m2")), CLAIM_ALL);

        // then
        assertThat(result.failed()).containsExactly("m1");
        assertThat(result.fetched()).containsExactly(new Fetched("m2", matchApi.match("m2"), null));
    }

    @Test
    @DisplayName("허가된 묶음은 다음 허가를 묻기 전에 시작하고, 남은 것이 끝내 거절되면 순서대로 돌려준다")
    void startsEachGrantedChunkBeforeAskingNext() {
        // given: 두 번째 요청(2건)만 허가한다. 4번째부터라 타임라인은 없다
        permitPort.allow = counts -> permitPort.requests.size() == 2;
        List<Item> items = List.of("m1", "m2", "m3", "m4").stream().map(matchId -> new Item(matchId, false)).toList();

        // when
        Result result = fetcher.fetch("KR", items, CLAIM_ALL);

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
        List<Item> all = items(List.of("m1", "m2", "m3", "m4"));

        // when
        Result result = fetcher.fetch("KR", all, CLAIM_ALL);

        // then
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactly("m1", "m2");
        assertThat(result.denied()).containsExactly(all.get(2), all.get(3));
        assertThat(matchApi.calls).containsExactly("match:m1", "timeline:m1", "match:m2", "timeline:m2");
    }

    @Test
    @DisplayName("선점 조회가 던지면 그 항목과 뒤의 항목은 거절된 것으로 둔다")
    void claimThrows_thatAndRestDenied() {
        // given
        List<Item> all = items(List.of("m1", "m2", "m3"));
        Predicate<String> claim = matchId -> {
            if (matchId.equals("m2")) {
                throw new IllegalStateException("redis down");
            }
            return true;
        };

        // when
        Result result = fetcher.fetch("KR", all, claim);

        // then
        assertThat(matchApi.calls).containsExactly("match:m1", "timeline:m1");
        assertThat(result.fetched()).extracting(Fetched::matchId).containsExactly("m1");
        assertThat(result.denied()).containsExactly(all.get(1), all.get(2));
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
