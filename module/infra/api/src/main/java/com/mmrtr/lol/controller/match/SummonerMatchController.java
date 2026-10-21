package com.mmrtr.lol.controller.match;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.controller.support.response.SliceResponse;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.domain.match.application.usecase.MatchStatsQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소환사별 전적·매치 ID·일별 게임 수·모스트 챔피언. lol-server match 모듈({@code MatchController})의 새 경로를 이전 (공개 API 재설계 3.1절).
 * 응답 본문은 lol-server 와 같다. page 는 0부터이고 생략하면 0 이다 — lol-ui 는 첫 페이지로 0 을 보낸다
 * (lol-server 의 전적 목록은 생략 시 1, 곧 두 번째 페이지였다).
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/summoners/{puuid}")
@RequiredArgsConstructor
public class SummonerMatchController {

    private final MatchQueryUseCase matchQueryUseCase;
    private final MatchStatsQueryUseCase matchStatsQueryUseCase;

    @GetMapping("/matches")
    public ApiResponse<SliceResponse<GameReadModel>> getMatches(
            @PathVariable("puuid") String puuid,
            @RequestParam(value = "season", required = false) Integer season,
            @RequestParam(value = "queueId", required = false) Integer queueId,
            @RequestParam(value = "page", defaultValue = "0") int page
    ) {
        return ApiResponse.success(SliceResponse.of(
                matchQueryUseCase.getSummonerMatches(puuid, season, queueId, page)));
    }

    @GetMapping("/match-ids")
    public ApiResponse<SliceResponse<String>> getMatchIds(
            @PathVariable("puuid") String puuid,
            @RequestParam(value = "queueId", required = false) Integer queueId,
            @RequestParam(value = "page", defaultValue = "0") int page
    ) {
        return ApiResponse.success(SliceResponse.of(matchQueryUseCase.getSummonerMatchIds(puuid, queueId, page)));
    }

    /** season 은 필수 — 없으면 400 (lol-server 와 같다). */
    @GetMapping("/matches/daily-count")
    public ApiResponse<DailyGameCountReadModel> getDailyGameCounts(
            @PathVariable("puuid") String puuid,
            @RequestParam("season") int season,
            @RequestParam(value = "queueId", required = false) Integer queueId
    ) {
        return ApiResponse.success(matchStatsQueryUseCase.getDailyGameCounts(puuid, season, queueId));
    }

    /** 모스트 챔피언. 옛 경로는 {@code /api/v1/rank/champions?puuid=}. */
    @GetMapping("/champions")
    public ApiResponse<RankChampionsReadModel> getRankChampions(
            @PathVariable("puuid") String puuid,
            @RequestParam(value = "season", required = false) Integer season
    ) {
        return ApiResponse.success(matchStatsQueryUseCase.getRankChampions(puuid, season));
    }
}
