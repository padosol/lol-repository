package com.mmrtr.lol.controller.internal;

import com.mmrtr.lol.domain.summoner.application.usecase.DuoProfileQueryUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel;
import com.mmrtr.lol.support.error.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * lol-server duo 용 내부 API (서비스 분리 설계 5절). 내부 포트에서만 받는다 — {@code PublicApiPathFilter}.
 * 계약: {@code docs/api/internal-v1.openapi.yaml} ({@code InternalApiContractTest} 가 생성 결과와 비교).
 */
@Tag(name = "summoners", description = "소환사 (lol-server duo 용)")
@RestController
@RequestMapping("/internal/v1/summoners")
@RequiredArgsConstructor
public class DuoProfileInternalController {

    private final DuoProfileQueryUseCase duoProfileQueryUseCase;

    @Operation(
            summary = "duo 프로필 (Riot ID·솔로 랭크 티어·모스트 챔피언·최근 게임 요약)",
            description = """
                    lol-server duo 가 모집글·신청 생성 시점에 스냅샷으로 저장하는 통계. 응답 형태는 lol-server `RiotAccountStats` 와 같다.
                    - gameName·tagLine: Riot ID. 매칭이 확정된 상대에게 보여 준다
                    - tierInfo: 솔로 랭크 티어. 기록이 없으면 `UNRANKED` / `I` / 0
                    - mostChampions: 솔로 랭크 판 수 상위 3개 (전 시즌, challenges 가 적재된 게임만 센다)
                    - recentGameSummary: 최근 솔로 랭크 20게임의 승패, playedChampions 는 최신 게임부터""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "색인된 적 없는 소환사 — lol-server 는 SUMMONER_SEARCH_REQUIRED 로 안내",
            content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    @GetMapping(path = "/{puuid}/duo-profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public DuoProfileReadModel getDuoProfile(@PathVariable("puuid") String puuid) {
        return duoProfileQueryUseCase.getDuoProfile(puuid);
    }
}
