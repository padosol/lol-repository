package com.mmrtr.lol.controller.ranking;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.controller.support.response.PageResponse;
import com.mmrtr.lol.domain.league.application.usecase.RankingQueryUseCase;
import com.mmrtr.lol.domain.league.domain.RankingQueue;
import com.mmrtr.lol.domain.league.readmodel.RankingReadModel;
import com.mmrtr.lol.domain.league.readmodel.TierCutoffReadModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리더보드·티어 컷오프. lol-server leaderboard({@code RankController})·gamedata({@code TierCutoffController})의
 * 새 경로를 이전 (공개 API 재설계 3.3절). 응답 본문은 lol-server 와 같다.
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingQueryUseCase rankingQueryUseCase;

    @GetMapping
    public ApiResponse<PageResponse<RankingReadModel>> getRankings(
            @RequestParam("platform") String platform,
            @RequestParam(name = "queue", defaultValue = "SOLO") RankingQueue queue,
            @RequestParam(name = "tier", required = false) String tier,
            @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        return ApiResponse.success(PageResponse.of(rankingQueryUseCase.getRankings(platform, queue, tier, page)));
    }

    @GetMapping("/tier-cutoffs")
    public ApiResponse<List<TierCutoffReadModel>> getTierCutoffs(
            @RequestParam("platform") String platform,
            @RequestParam(name = "queue", required = false) String queue,
            @RequestParam(name = "tier", required = false) String tier
    ) {
        return ApiResponse.success(rankingQueryUseCase.getTierCutoffs(platform, queue, tier));
    }
}
