package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.application.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerLookupLockPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerQueryPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.domain.summoner.application.usecase.SummonerQueryUseCase;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.RiotId;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerAutocompleteReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * lol-server {@code SummonerService}·{@code LeagueService} 조회의 이전.
 * 서비스 단위 트랜잭션은 두지 않는다 — Riot 에서 찾은 소환사 저장({@link SaveSummonerDataUseCase})이 쓰기 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
public class SummonerQueryService implements SummonerQueryUseCase {

    /** lol-ui 는 결과를 전부 목록으로 그린다. lol-server 는 상한이 없어 짧은 검색어에 수천 건을 돌려줄 수 있다. */
    static final int AUTOCOMPLETE_LIMIT = 20;

    /** 저장된 소환사가 하나인지만 알면 된다. */
    private static final int UNIQUE_CHECK_LIMIT = 2;

    private final SummonerQueryPort summonerQueryPort;
    private final SummonerApiPort summonerApiPort;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final SummonerLookupLockPort summonerLookupLockPort;
    private final Executor requestExecutor;

    @Override
    public SummonerReadModel getSummonerByPuuid(String puuid) {
        return summonerQueryPort.findSummoner(puuid)
                .map(SummonerReadModel::from)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_PUUID));
    }

    @Override
    public SummonerReadModel getSummonerByRiotId(String riotId, String platform) {
        Platform resolvedPlatform = resolvePlatform(platform);
        RiotId id = RiotId.parse(riotId);

        Optional<Summoner> stored = findStoredSummoner(id, resolvedPlatform);
        if (stored.isPresent()) {
            return SummonerReadModel.from(stored.get());
        }
        if (!id.hasTagLine()) {
            // Riot account-v1 은 태그 없이 찾을 수 없다 (lol-server 는 태그 "null" 로 호출해 500 이 난다)
            throw new CoreException(ErrorType.NOT_FOUND_USER);
        }

        // 락을 기다린 요청은 먼저 잡은 요청이 저장한 소환사를 다시 찾아 Riot 을 호출하지 않는다
        Summoner summoner = summonerLookupLockPort
                .executeWithLock(resolvedPlatform.getPlatformId() + ":" + id.searchName(),
                        () -> findStoredSummoner(id, resolvedPlatform)
                                .orElseGet(() -> fetchAndSave(id, resolvedPlatform)))
                .orElseThrow(() -> new CoreException(ErrorType.LOCK_ACQUISITION_FAILED));
        return SummonerReadModel.from(summoner);
    }

    @Override
    public List<SummonerAutocompleteReadModel> autocomplete(String q, String platform) {
        String platformId = resolvePlatform(platform).getPlatformId();
        String prefix = RiotId.normalize(q);
        if (prefix.isEmpty()) {
            // lol-server 는 빈 검색어에 그 플랫폼의 랭크 소환사 전부를 돌려준다
            return List.of();
        }
        return summonerQueryPort.findRankedSummonersBySearchNamePrefix(platformId, prefix, AUTOCOMPLETE_LIMIT)
                .stream()
                .map(SummonerAutocompleteReadModel::from)
                .toList();
    }

    @Override
    public SummonerLeagueReadModel getLeagues(String puuid) {
        return SummonerLeagueReadModel.of(
                summonerQueryPort.findLeagues(puuid),
                summonerQueryPort.findLeagueHistories(puuid));
    }

    /** 하나로 특정될 때만 — 여럿이면(개명 전 행이 남은 경우 등) Riot 에서 현재 소환사를 찾는다 (lol-server 와 같다). */
    private Optional<Summoner> findStoredSummoner(RiotId id, Platform platform) {
        List<Summoner> found = id.hasTagLine()
                ? summonerQueryPort.findSummonersBySearchName(platform.getPlatformId(), id.searchName(),
                        UNIQUE_CHECK_LIMIT)
                : summonerQueryPort.findSummonersBySearchNamePrefix(platform.getPlatformId(), id.searchNamePrefix(),
                        UNIQUE_CHECK_LIMIT);
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    /** {@code /api/riot/{region}/summoners/{gameName}/{tagLine}} 과 같이 Riot 에서 찾아 저장한다. */
    private Summoner fetchAndSave(RiotId id, Platform platform) {
        Summoner summoner;
        try {
            summoner = summonerApiPort
                    .fetchSummonerByRiotId(id.gameName(), id.tagLine(), platform.name(), requestExecutor)
                    .join();
        } catch (CompletionException e) {
            // 어댑터가 NOT_FOUND_USER·TOO_MANY_REQUESTS 로 바꾼 예외를 그대로 응답에 쓴다
            if (e.getCause() instanceof CoreException coreException) {
                throw coreException;
            }
            throw e;
        }
        // 첫 갱신이 revision 비교에서 "변경 없음"으로 끝나 매치 수집을 건너뛰지 않도록 갱신 일시를 과거로 둔다
        summoner.initSummoner();
        saveSummonerDataUseCase.execute(summoner);
        return summoner;
    }

    /** 플랫폼 이름(kr, na …)을 받는다. 모르는 값은 400 (rankings 와 같다, lol-server 는 검증하지 않는다). */
    private Platform resolvePlatform(String platform) {
        Platform resolved = Platform.valueOfName(platform);
        if (resolved == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "유효하지 않은 platform 입니다.");
        }
        return resolved;
    }
}
