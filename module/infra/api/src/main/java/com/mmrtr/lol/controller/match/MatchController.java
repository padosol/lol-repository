package com.mmrtr.lol.controller.match;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매치 단건·타임라인. lol-server match 모듈({@code MatchController})의 새 경로를 이전 (공개 API 재설계 3.2절).
 * 응답 본문은 lol-server 와 같다. 타임라인의 옛 경로 {@code /api/v1/match/timeline/{matchId}} 는 옮기지 않는다.
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchQueryUseCase matchQueryUseCase;

    @GetMapping("/{matchId}")
    public ApiResponse<GameReadModel> getMatch(@PathVariable("matchId") String matchId) {
        return ApiResponse.success(matchQueryUseCase.getGame(matchId));
    }

    @GetMapping("/{matchId}/timeline")
    public ApiResponse<MatchTimelineReadModel> getTimeline(@PathVariable("matchId") String matchId) {
        return ApiResponse.success(matchQueryUseCase.getTimeline(matchId));
    }
}
