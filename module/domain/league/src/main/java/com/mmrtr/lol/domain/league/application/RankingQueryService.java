package com.mmrtr.lol.domain.league.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.league.application.port.RankingQueryPort;
import com.mmrtr.lol.domain.league.application.usecase.RankingQueryUseCase;
import com.mmrtr.lol.domain.league.domain.RankingQueue;
import com.mmrtr.lol.domain.league.readmodel.RankingReadModel;
import com.mmrtr.lol.domain.league.readmodel.TierCutoffReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.springframework.util.StringUtils.hasText;

/**
 * lol-server leaderboard({@code RankService})·gamedata({@code TierCutoffService}) 조회의 이전.
 * 서비스 단위 트랜잭션은 두지 않는다 — 페이지 조회(본문+COUNT)는 어댑터 메서드가 한 트랜잭션으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class RankingQueryService implements RankingQueryUseCase {

    static final int PAGE_SIZE = 50;

    /** 이보다 큰 page 는 JPA offset 이 int 를 넘어 500 이 난다. 데이터가 있을 수 없는 범위라 400 으로 끊는다. */
    static final int MAX_PAGE = Integer.MAX_VALUE / PAGE_SIZE;

    private final RankingQueryPort rankingQueryPort;

    @Override
    public PageResult<RankingReadModel> getRankings(String platform, RankingQueue queue, String tier, int page) {
        if (page < 1 || page > MAX_PAGE) {
            throw new CoreException(ErrorType.INVALID_INPUT, "page 는 1 이상 " + MAX_PAGE + " 이하여야 합니다: " + page);
        }
        // queue=&queue= 처럼 값이 여러 개 비어 오면 defaultValue 가 적용되지 않고 null 로 바인딩된다
        RankingQueue resolvedQueue = queue != null ? queue : RankingQueue.SOLO;
        return rankingQueryPort.findRankings(resolvePlatformId(platform), resolvedQueue.queueName(), upperOrNull(tier),
                        page, PAGE_SIZE)
                .map(RankingReadModel::from);
    }

    @Override
    public List<TierCutoffReadModel> getTierCutoffs(String platform, String queue, String tier) {
        String platformId = resolvePlatformId(platform);
        if (hasText(tier) && !hasText(queue)) {
            throw new CoreException(ErrorType.INVALID_INPUT, "tier 필터는 queue 와 함께 지정해야 합니다.");
        }
        return rankingQueryPort.findTierCutoffs(platformId, canonicalQueue(queue), upperOrNull(tier)).stream()
                .map(TierCutoffReadModel::from)
                .toList();
    }

    /**
     * 플랫폼 이름(kr, na …)을 저장 값(KR, NA1 …)으로. 모르는 값은 빈 결과가 아니라 400 이다.
     * 메시지에 입력값을 넣지 않는다 — 예외 메시지가 그대로 로그에 남아 개행 주입으로 로그를 위조할 수 있다.
     */
    private String resolvePlatformId(String platform) {
        Platform resolved = Platform.valueOfName(platform);
        if (resolved == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "유효하지 않은 platform 입니다.");
        }
        return resolved.getPlatformId();
    }

    /**
     * queue 를 저장된 {@link Queue} 이름 표기로 맞춘다 (대소문자 무시). {@code RANKED_SOLO_5x5} 처럼 소문자가 섞여
     * {@code toUpperCase()} 로는 맞출 수 없다. 모르는 값은 그대로 두어 빈 결과가 되게 한다 (lol-server 와 같다).
     */
    private String canonicalQueue(String queue) {
        if (!hasText(queue)) {
            return null;
        }
        return Arrays.stream(Queue.values())
                .map(Queue::name)
                .filter(name -> name.equalsIgnoreCase(queue))
                .findFirst()
                .orElse(queue);
    }

    private String upperOrNull(String value) {
        return hasText(value) ? value.toUpperCase(Locale.ROOT) : null;
    }
}
