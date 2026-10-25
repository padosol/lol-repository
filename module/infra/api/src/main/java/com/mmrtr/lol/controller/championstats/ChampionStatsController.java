package com.mmrtr.lol.controller.championstats;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.domain.championstats.application.usecase.ChampionStatsQueryUseCase;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챔피언 통계 (BigQuery). lol-server championstats({@code ChampionStatsController})의 새 경로를 이전
 * (공개 API 재설계 3.4절). 응답 본문은 lol-server 와 같다.
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/champion-stats")
@RequiredArgsConstructor
public class ChampionStatsController {

    private final ChampionStatsQueryUseCase championStatsQueryUseCase;

    @GetMapping
    public ApiResponse<List<PositionChampionStatsReadModel>> getChampionStatsByPosition(
            @RequestParam("platform") String platform,
            @RequestParam("patch") String patch,
            @RequestParam("tier") String tier
    ) {
        return ApiResponse.success(
                championStatsQueryUseCase.getChampionStatsByPosition(platform, patch, restorePlus(tier)));
    }

    @GetMapping("/{championId}")
    public ApiResponse<ChampionStatsReadModel> getChampionStats(
            @PathVariable("championId") int championId,
            @RequestParam("platform") String platform,
            @RequestParam("patch") String patch,
            @RequestParam("tier") String tier
    ) {
        return ApiResponse.success(
                championStatsQueryUseCase.getChampionStats(championId, platform, patch, restorePlus(tier)));
    }

    /**
     * query 의 '+' 는 공백으로 디코딩되므로 (RFC 1866) {@code tier=MASTER+} 는 "MASTER " 로 들어온다.
     * 끝 공백은 원래 '+' 였던 것으로 되돌린다 (lol-server 와 같다).
     */
    private static String restorePlus(String tier) {
        String stripped = tier.strip();
        return tier.endsWith(" ") ? stripped + "+" : stripped;
    }
}
