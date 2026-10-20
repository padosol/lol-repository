package com.mmrtr.lol.controller.summoner;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.domain.summoner.application.usecase.SummonerRenewalUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerRenewalReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소환사 전적 갱신 요청·상태. lol-server {@code SummonerController} 의 새 경로를 이전 (공개 API 재설계 3.1절).
 * 갱신 자체는 MQ 컨슈머({@code SummonerRenewalListener})가 비동기로 한다.
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/summoners")
@RequiredArgsConstructor
public class SummonerRenewalController {

    private final SummonerRenewalUseCase summonerRenewalUseCase;

    /** 접수(또는 이미 진행 중) → 202, 상태를 폴링한다. 쿨다운으로 거절 → 200 + FAILED, 폴링하지 않는다 (lol-server 와 같다). */
    @PostMapping("/{puuid}/renewal")
    public ResponseEntity<ApiResponse<SummonerRenewalReadModel>> requestRenewal(@PathVariable("puuid") String puuid) {
        SummonerRenewalReadModel renewal = summonerRenewalUseCase.requestRenewal(puuid);
        HttpStatus status = renewal.rejected() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(renewal));
    }

    @GetMapping("/{puuid}/renewal")
    public ApiResponse<SummonerRenewalReadModel> getRenewalStatus(@PathVariable("puuid") String puuid) {
        return ApiResponse.success(summonerRenewalUseCase.getRenewalStatus(puuid));
    }
}
