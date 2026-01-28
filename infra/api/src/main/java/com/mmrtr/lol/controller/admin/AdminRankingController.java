package com.mmrtr.lol.controller.admin;

import com.mmrtr.lol.controller.admin.response.RankingCalculateResponse;
import com.mmrtr.lol.domain.league.service.usecase.TriggerSummonerRankingCalculationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ranking")
@RequiredArgsConstructor
public class AdminRankingController {

    private final TriggerSummonerRankingCalculationUseCase triggerSummonerRankingCalculationUseCase;

    @PostMapping("/calculate")
    public ResponseEntity<RankingCalculateResponse> calculateRanking() {
        triggerSummonerRankingCalculationUseCase.execute();
        return ResponseEntity.ok(RankingCalculateResponse.of());
    }
}
