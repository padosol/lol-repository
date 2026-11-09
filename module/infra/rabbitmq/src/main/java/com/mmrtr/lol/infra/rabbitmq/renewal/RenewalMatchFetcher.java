package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.rabbitmq.listener.RiotFailures;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchPlan.Item;
import com.mmrtr.lol.support.riot.PermitRounds;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 갱신 매치 단계의 항목을 USER 묶음 허가로 받는다. 묶음은 {@value #BATCH_SIZE}개에서 시작해 거절되면 절반씩 줄인다
 * ({@link PermitRounds}). 허가된 묶음마다 match ∥ timeline 을 바로 시작하고, 모든 묶음을 시작한 뒤 한꺼번에 기다려 나눈다.
 * 실패한 호출을 다시 보내지 않는다. 다시 받는 일은 결과를 받은 단계가 큐로 넘긴다.
 */
@Slf4j
@Component
public class RenewalMatchFetcher {

    static final int BATCH_SIZE = 20;

    private final MatchApiPort matchApiPort;
    private final RiotPermitPort permitPort;
    private final Executor requestExecutor;

    public RenewalMatchFetcher(MatchApiPort matchApiPort, RiotPermitPort permitPort, Executor requestExecutor) {
        this.matchApiPort = matchApiPort;
        this.permitPort = permitPort;
        this.requestExecutor = requestExecutor;
    }

    /**
     * 항목을 받는다. 허가를 묻다 예외가 나면 이미 시작한 묶음은 그대로 받고, 아직 시작하지 못한 항목은 거절된 것으로 둔다.
     */
    Result fetch(String platform, List<Item> items) {
        List<Pending> started = new ArrayList<>();
        List<Item> denied;
        try {
            denied = PermitRounds.acquire(permitPort, platform, RiotCallPriority.USER, items, BATCH_SIZE,
                    Item::cost, (chunk, permits) -> chunk.forEach(item -> started.add(start(item, platform, permits))));
            if (!denied.isEmpty()) {
                log.warn("갱신 매치 묶음 허가 거절. platform={}, matchIds={}",
                        platform, denied.stream().map(Item::matchId).toList());
            }
        } catch (RuntimeException e) {
            // PermitRounds 는 앞에서부터 허가하므로 시작한 항목은 items 의 앞부분이다.
            denied = items.subList(started.size(), items.size());
            log.warn("갱신 매치 허가 조회 실패. 시작하지 못한 항목을 거절된 것으로 둔다. platform={}, matchIds={}, cause={}",
                    platform, denied.stream().map(Item::matchId).toList(), e.toString());
        }
        Result collected = new Result(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                List.of());
        started.forEach(pending -> classify(pending, collected));
        return new Result(List.copyOf(collected.fetched()), List.copyOf(collected.userRetry()),
                List.copyOf(collected.timelineLater()), List.copyOf(collected.clientError()), List.copyOf(denied));
    }

    /** 허가된 묶음의 permits 에서 match·timeline 허가를 하나씩 꺼내 부른다. 부르다 던지면 그 호출이 실패한 것으로 둔다. */
    private Pending start(Item item, String platform, RiotPermits permits) {
        CompletableFuture<MatchDto> match = item.match()
                ? call(() -> matchApiPort.fetchMatchById(item.matchId(), platform, permits, requestExecutor))
                : null;
        CompletableFuture<TimelineDto> timeline = item.timeline()
                ? call(() -> matchApiPort.fetchTimelineById(item.matchId(), platform, permits, requestExecutor))
                : null;
        return new Pending(item, match, timeline);
    }

    /**
     * match 를 받으면 fetched 다 (timeline 을 받지 못하면 null 로 두고 timelineLater 에도 넣는다).
     * match 가 4xx(429 제외)면 clientError, 그 밖의 실패면 userRetry 이고 timeline 결과는 버린다.
     * 타임라인만 받는 항목은 받으면 fetched, 4xx(429 제외)면 버리고, 그 밖에 못 받으면 timelineLater 다.
     * 빈 본문은 받지 못한 것으로 본다.
     */
    private static void classify(Pending pending, Result collected) {
        String matchId = pending.item().matchId();
        Throwable timelineFailure = pending.timeline() == null ? null : failureOf(pending.timeline());
        TimelineDto timeline = pending.timeline() == null || timelineFailure != null ? null : pending.timeline().join();
        if (!pending.item().match()) {
            if (timeline != null) {
                collected.fetched().add(new Fetched(matchId, null, timeline));
            } else if (RiotFailures.isClientError(timelineFailure)) {
                // 다시 받아도 같은 4xx 라 나중 타임라인으로 넘기지 않는다
                log.warn("갱신 타임라인 조회 4xx. 나중 타임라인으로 넘기지 않고 버린다. matchId={}, timeline={}",
                        matchId, describe(timelineFailure));
            } else {
                log.warn("갱신 타임라인 조회 실패. 나중 타임라인으로 넘긴다. matchId={}, timeline={}",
                        matchId, describe(timelineFailure));
                collected.timelineLater().add(matchId);
            }
            return;
        }
        Throwable matchFailure = failureOf(pending.match());
        MatchDto match = matchFailure == null ? pending.match().join() : null;
        if (match == null) {
            boolean clientError = RiotFailures.isClientError(matchFailure);
            log.warn("갱신 매치 조회 실패. matchId={}, clientError={}, match={}",
                    matchId, clientError, describe(matchFailure));
            (clientError ? collected.clientError() : collected.userRetry()).add(matchId);
            return;
        }
        collected.fetched().add(new Fetched(matchId, match, timeline));
        if (pending.item().timeline() && timeline == null) {
            log.warn("갱신 타임라인 조회 실패. 매치는 저장하고 타임라인은 나중에 받는다. matchId={}, timeline={}",
                    matchId, describe(timelineFailure));
            collected.timelineLater().add(matchId);
        }
    }

    private static <T> CompletableFuture<T> call(Supplier<CompletableFuture<T>> request) {
        try {
            return request.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Throwable failureOf(CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException | CancellationException e) {
            return e;
        }
    }

    private static String describe(Throwable failure) {
        if (failure == null) {
            return "empty body";
        }
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        return cause.toString();
    }

    /** 시작한 항목. 요청하지 않은 쪽은 null 이다. */
    private record Pending(Item item, CompletableFuture<MatchDto> match, CompletableFuture<TimelineDto> timeline) {
    }

    /** 받은 것. 타임라인만 받은 항목은 match 가, 타임라인을 받지 못한(또는 요청하지 않은) 매치는 timeline 이 null 이다. */
    record Fetched(String matchId, MatchDto match, TimelineDto timeline) {
    }

    /**
     * @param fetched       받은 것 (저장한다)
     * @param userRetry     match 가 429·5xx·IO·시간 초과 등으로 실패한 매치
     * @param timelineLater 타임라인을 받지 못한 id (match 는 받았거나, 타임라인만 받는 항목 — 그 4xx 는 빠진다)
     * @param clientError   match 가 4xx(429 제외)로 실패한 매치
     * @param denied        세 번 모두 허가가 거절된 항목 (허가 조회 예외로 시작하지 못한 것 포함)
     */
    record Result(List<Fetched> fetched, List<String> userRetry, List<String> timelineLater,
                  List<String> clientError, List<Item> denied) {
    }
}
