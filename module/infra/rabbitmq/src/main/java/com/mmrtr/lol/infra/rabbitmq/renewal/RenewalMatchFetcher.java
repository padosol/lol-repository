package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 갱신 매치 단계의 항목을 USER 묶음 허가로 받는다. 묶음은 {@value #BATCH_SIZE}개에서 시작해 거절되면 1개까지 절반씩
 * 줄인다({@link PermitRounds}). 허가된 묶음의 항목마다 선점을 잡고, 잡은 항목만 match ∥ timeline 을 바로 시작한다.
 * 모든 묶음을 시작한 뒤 한꺼번에 기다려 나눈다. 실패한 호출을 다시 보내지 않는다.
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
     * 항목을 받는다. 허가된 항목은 claim 이 true 일 때만 부르고, false 면 다른 갱신이 선점한 것으로 둔다(허가는 버린다).
     * 허가를 묻거나 선점을 잡다 예외가 나면 이미 처리한 앞부분은 그대로 받고, 나머지는 거절된 것으로 둔다.
     */
    Result fetch(String platform, List<Item> items, Predicate<String> claim) {
        List<Pending> started = new ArrayList<>();
        List<String> claimedElsewhere = new ArrayList<>();
        List<Item> denied;
        try {
            denied = PermitRounds.acquire(permitPort, platform, RiotCallPriority.USER, items, BATCH_SIZE,
                    Item::cost, (chunk, permits) -> chunk.forEach(item -> {
                        if (claim.test(item.matchId())) {
                            started.add(start(item, platform, permits));
                        } else {
                            claimedElsewhere.add(item.matchId());
                        }
                    }));
            if (!denied.isEmpty()) {
                log.warn("갱신 매치 묶음 허가 거절. platform={}, matchIds={}",
                        platform, denied.stream().map(Item::matchId).toList());
            }
        } catch (RuntimeException e) {
            // PermitRounds 는 앞에서부터 허가하고 묶음 안도 앞에서부터 처리하므로, 처리한 항목은 items 의 앞부분이다.
            denied = items.subList(started.size() + claimedElsewhere.size(), items.size());
            log.warn("갱신 매치 허가·선점 조회 실패. 처리하지 못한 항목을 거절된 것으로 둔다. platform={}, matchIds={}, cause={}",
                    platform, denied.stream().map(Item::matchId).toList(), e.toString());
        }
        List<Fetched> fetched = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        started.forEach(pending -> classify(pending, fetched, failed));
        return new Result(List.copyOf(fetched), List.copyOf(failed), List.copyOf(claimedElsewhere),
                List.copyOf(denied));
    }

    /** 허가된 묶음의 permits 에서 match·timeline 허가를 하나씩 꺼내 부른다. 부르다 던지면 그 호출이 실패한 것으로 둔다. */
    private Pending start(Item item, String platform, RiotPermits permits) {
        CompletableFuture<MatchDto> match =
                call(() -> matchApiPort.fetchMatchById(item.matchId(), platform, permits, requestExecutor));
        CompletableFuture<TimelineDto> timeline = item.timeline()
                ? call(() -> matchApiPort.fetchTimelineById(item.matchId(), platform, permits, requestExecutor))
                : null;
        return new Pending(item, match, timeline);
    }

    /**
     * match 를 받으면 fetched 다 (timeline 을 받지 못했거나 요청하지 않았으면 null). match 가 어떤 이유로든
     * 실패하거나 본문이 비면 failed 다. 빈 타임라인 본문은 받지 못한 것으로 본다.
     */
    private static void classify(Pending pending, List<Fetched> fetched, List<String> failed) {
        String matchId = pending.item().matchId();
        Throwable matchFailure = failureOf(pending.match());
        MatchDto match = matchFailure == null ? pending.match().join() : null;
        if (match == null) {
            log.warn("갱신 매치 조회 실패. 누락으로 둔다. matchId={}, match={}", matchId, describe(matchFailure));
            failed.add(matchId);
            return;
        }
        TimelineDto timeline = null;
        if (pending.timeline() != null) {
            Throwable timelineFailure = failureOf(pending.timeline());
            timeline = timelineFailure == null ? pending.timeline().join() : null;
            if (timeline == null) {
                log.warn("갱신 타임라인 조회 실패. 매치는 저장하고 타임라인은 나중에 받는다. matchId={}, timeline={}",
                        matchId, describe(timelineFailure));
            }
        }
        fetched.add(new Fetched(matchId, match, timeline));
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

    /** 시작한 항목. 타임라인을 요청하지 않았으면 timeline 이 null 이다. */
    private record Pending(Item item, CompletableFuture<MatchDto> match, CompletableFuture<TimelineDto> timeline) {
    }

    /** 받은 매치. 타임라인을 받지 못했거나 요청하지 않았으면 timeline 이 null 이다. */
    record Fetched(String matchId, MatchDto match, TimelineDto timeline) {
    }

    /**
     * @param fetched          받은 매치 (저장한다)
     * @param failed           match 가 429·5xx·4xx·IO·시간 초과 등으로 실패했거나 본문이 빈 매치 (누락)
     * @param claimedElsewhere 허가 뒤 선점을 잡지 못해 부르지 않은 매치 (다른 갱신이 받는다)
     * @param denied           1개까지 줄여도 허가가 거절된 항목 (허가·선점 조회 예외로 처리하지 못한 것 포함)
     */
    record Result(List<Fetched> fetched, List<String> failed, List<String> claimedElsewhere, List<Item> denied) {
    }
}
