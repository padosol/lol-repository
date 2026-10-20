package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MatchCacheReadPort;
import com.mmrtr.lol.domain.match.application.port.MatchQueryPort;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.Team;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.page.SliceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** lol-server {@code MatchService} 조회의 이전. */
@Service
@RequiredArgsConstructor
public class MatchQueryService implements MatchQueryUseCase {

    static final int PAGE_SIZE = 20;
    private static final int FIRST_PAGE = 0;

    /** 전적 목록 DB 쿼리가 보여주는 게임 모드. 캐시 오버레이도 같은 제한을 걸어야 목록 구성이 어긋나지 않는다. */
    private static final Set<String> LISTABLE_GAME_MODES = Set.of("CLASSIC", "CHERRY");

    private final MatchQueryPort matchQueryPort;
    private final MatchCacheReadPort matchCacheReadPort;
    private final Executor requestExecutor;

    @Override
    public GameReadModel getGame(String matchId) {
        GameInfo gameInfo = matchQueryPort.findGameInfo(matchId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_MATCH_ID));
        GameReadModel game = loadGames(List.of(gameInfo)).get(0);
        if (game.teamInfoData() == null) {
            // lol-server 단건 조회는 참가자가 없어도 빈 팀 정보를 둔다 (목록 조회는 null)
            return new GameReadModel(game.gameInfoData(), game.participantData(), new Team(null, null));
        }
        return game;
    }

    @Override
    public MatchTimelineReadModel getTimeline(String matchId) {
        return MatchTimelineReadModel.from(matchQueryPort.findTimelineEvents(List.of(matchId)));
    }

    /**
     * DB 조회는 항상 하고, 첫 페이지에만 캐시 오버레이를 얹는다. 캐시 ZSET 은 20건으로 잘려 목록 전체의 원천이 될 수 없으므로
     * 시즌·큐 필터와 페이징은 DB 가 맡고, 캐시는 "갱신 직후 DB 일괄 저장이 아직 안 끝난 최신 매치" 만 보탠다.
     */
    @Override
    public SliceResult<GameReadModel> getSummonerMatches(String puuid, Integer season, Integer queueId, int page) {
        validatePage(page);
        SliceResult<GameInfo> gameInfos =
                matchQueryPort.findSummonerGameInfos(puuid, season, queueId, page, PAGE_SIZE);
        SliceResult<GameReadModel> dbResult =
                new SliceResult<>(loadGames(gameInfos.content()), gameInfos.hasNext());
        if (page != FIRST_PAGE) {
            return dbResult;
        }
        return overlayCachedMatches(puuid, season, queueId, dbResult);
    }

    @Override
    public SliceResult<String> getSummonerMatchIds(String puuid, Integer queueId, int page) {
        validatePage(page);
        return matchQueryPort.findSummonerMatchIds(puuid, queueId, page, PAGE_SIZE);
    }

    private void validatePage(int page) {
        if (page < 0) {
            // lol-server 는 음수 page 로 500 이 난다
            throw new CoreException(ErrorType.INVALID_INPUT);
        }
    }

    /** 참가자·타임라인 이벤트를 매치들 몫으로 한 번씩, 동시에 읽어 조립한다. */
    private List<GameReadModel> loadGames(List<GameInfo> gameInfos) {
        if (gameInfos.isEmpty()) {
            return List.of();
        }
        List<String> matchIds = gameInfos.stream().map(GameInfo::matchId).toList();
        CompletableFuture<Map<String, List<ParticipantRecord>>> participants = CompletableFuture.supplyAsync(
                () -> matchQueryPort.findParticipants(matchIds).stream()
                        .collect(Collectors.groupingBy(ParticipantRecord::matchId)),
                requestExecutor);
        CompletableFuture<Map<String, List<Event>>> events = CompletableFuture.supplyAsync(
                () -> matchQueryPort.findTimelineEvents(matchIds).stream()
                        .collect(Collectors.groupingBy(Event::matchId)),
                requestExecutor);

        Map<String, List<ParticipantRecord>> participantsByMatch = participants.join();
        Map<String, List<Event>> eventsByMatch = events.join();
        return gameInfos.stream()
                .map(gameInfo -> GameReadModel.assemble(gameInfo,
                        participantsByMatch.getOrDefault(gameInfo.matchId(), List.of()),
                        eventsByMatch.getOrDefault(gameInfo.matchId(), List.of())))
                .toList();
    }

    private SliceResult<GameReadModel> overlayCachedMatches(
            String puuid, Integer season, Integer queueId, SliceResult<GameReadModel> dbResult) {
        List<String> cachedIds = matchCacheReadPort.findRecentMatchIds(puuid);
        if (cachedIds.isEmpty()) {
            return dbResult;
        }

        List<GameReadModel> dbContent = dbResult.content();
        Set<String> dbMatchIds = new HashSet<>();
        for (GameReadModel game : dbContent) {
            dbMatchIds.add(game.gameInfoData().matchId());
        }
        List<String> candidateIds = cachedIds.stream()
                .filter(matchId -> !dbMatchIds.contains(matchId))
                .toList();
        if (candidateIds.isEmpty()) {
            return dbResult;
        }

        List<GameReadModel> overlay = collectOverlay(candidateIds, season, queueId, pageFloorOf(dbContent));
        if (overlay.isEmpty()) {
            return dbResult;
        }
        overlay.sort(Comparator.comparingLong(MatchQueryService::orderKeyOf).reversed());

        // DB 첫 페이지 꼬리를 잘라내면 그 매치가 어느 페이지에서도 안 보이므로 자르지 않는다
        List<GameReadModel> merged = new ArrayList<>(overlay);
        merged.addAll(dbContent);
        return new SliceResult<>(merged, dbResult.hasNext());
    }

    private List<GameReadModel> collectOverlay(
            List<String> candidateIds, Integer season, Integer queueId, long pageFloor) {
        Map<String, GameReadModel> cachedGames = matchCacheReadPort.findGames(candidateIds);

        List<GameReadModel> overlay = new ArrayList<>();
        for (String matchId : candidateIds) {
            // 캐시에 없으면 DB 로 다시 찾지 않는다. DB 에 있는 매치는 이미 DB 페이지가 맡는다
            GameReadModel game = cachedGames.get(matchId);
            if (game == null || game.gameInfoData() == null) {
                continue;
            }
            // DB 첫 페이지 맨 아래보다 오래된 매치는 2페이지와 겹쳐 중복·순서 역전을 만든다
            if (orderKeyOf(game) <= pageFloor) {
                continue;
            }
            if (!isListableGameMode(game.gameInfoData())) {
                continue;
            }
            if (queueId != null && game.gameInfoData().queueId() != queueId) {
                continue;
            }
            if (season != null && !seasonMatches(game.gameInfoData(), season)) {
                continue;
            }
            overlay.add(game);
        }
        return overlay;
    }

    /** DB 첫 페이지의 가장 오래된 매치 정렬 키. 페이지가 비었으면 오버레이를 제한하지 않는다. */
    private static long pageFloorOf(List<GameReadModel> dbContent) {
        return dbContent.stream()
                .mapToLong(MatchQueryService::orderKeyOf)
                .min()
                .orElse(Long.MIN_VALUE);
    }

    /** DB 목록이 게임 종료 시각 최신순이므로 오버레이도 같은 키로 비교·정렬한다. */
    private static long orderKeyOf(GameReadModel game) {
        return game.gameInfoData().gameEndTimestamp();
    }

    private static boolean isListableGameMode(GameInfo gameInfo) {
        String gameMode = gameInfo.gameMode();
        return gameMode != null && LISTABLE_GAME_MODES.contains(gameMode.toUpperCase(Locale.ROOT));
    }

    /**
     * 캐시에는 시즌이 없으므로 게임 버전의 첫 숫자로 판별한다 — 저장 시 {@code match.season} 을 같은 규칙으로 채운다.
     * 판별할 수 없으면 시즌 필터 결과를 흐리지 않도록 뺀다.
     */
    private static boolean seasonMatches(GameInfo gameInfo, int season) {
        String gameVersion = gameInfo.gameVersion();
        if (gameVersion == null || gameVersion.isBlank()) {
            return false;
        }
        int dotIndex = gameVersion.indexOf('.');
        String major = dotIndex < 0 ? gameVersion : gameVersion.substring(0, dotIndex);
        try {
            return Integer.parseInt(major.trim()) == season;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
