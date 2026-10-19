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

/**
 * lol-server leaderboard({@code RankService})·gamedata({@code TierCutoffService}) 조회의 이전.
 * 서비스 단위 트랜잭션은 두지 않는다 — 조회마다 리포지토리 호출이 한 번이고 지연 로딩이 없다.
 */
@Service
@RequiredArgsConstructor
public class RankingQueryService implements RankingQueryUseCase {

    static final int PAGE_SIZE = 50;

    private final RankingQueryPort rankingQueryPort;

    @Override
    public PageResult<RankingReadModel> getRankings(String platform, RankingQueue queue, String tier, int page) {
        if (page < 1) {
            throw new CoreException(ErrorType.INVALID_INPUT, "page 는 1 이상이어야 합니다: " + page);
        }
        return rankingQueryPort.findRankings(resolvePlatformId(platform), queue.queueName(), upperOrNull(tier),
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

    /** 플랫폼 이름(kr, na …)을 저장 값(KR, NA1 …)으로. 모르는 값은 빈 결과가 아니라 400 이다. */
    private String resolvePlatformId(String platform) {
        Platform resolved = Platform.valueOfName(platform);
        if (resolved == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "유효하지 않은 platform 입니다: " + platform);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
