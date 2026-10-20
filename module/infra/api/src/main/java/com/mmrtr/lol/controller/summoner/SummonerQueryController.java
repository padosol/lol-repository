package com.mmrtr.lol.controller.summoner;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.domain.summoner.application.usecase.SummonerQueryUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerAutocompleteReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 소환사·리그 조회. lol-server summoner 모듈({@code SummonerController}·{@code LeagueController})의 새 경로를 이전
 * (공개 API 재설계 3.1절). 응답 본문은 lol-server 와 같다. 고정 세그먼트(autocomplete·by-riot-id)가 {@code {puuid}} 보다 우선한다.
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/summoners")
@RequiredArgsConstructor
public class SummonerQueryController {

    private final SummonerQueryUseCase summonerQueryUseCase;

    /** @param gameName {@code gameName-tagLine} (lol-server 와 같은 경로 변수 이름) */
    @GetMapping("/by-riot-id/{gameName}")
    public ApiResponse<SummonerReadModel> getSummonerByRiotId(
            @PathVariable("gameName") String gameName,
            @RequestParam("platform") String platform
    ) {
        return ApiResponse.success(summonerQueryUseCase.getSummonerByRiotId(gameName, platform));
    }

    @GetMapping("/autocomplete")
    public ApiResponse<List<SummonerAutocompleteReadModel>> autocomplete(
            @RequestParam("q") String q,
            @RequestParam("platform") String platform
    ) {
        return ApiResponse.success(summonerQueryUseCase.autocomplete(q, platform));
    }

    @GetMapping("/{puuid}")
    public ApiResponse<SummonerReadModel> getSummonerByPuuid(@PathVariable("puuid") String puuid) {
        return ApiResponse.success(summonerQueryUseCase.getSummonerByPuuid(puuid));
    }

    @GetMapping("/{puuid}/leagues")
    public ApiResponse<SummonerLeagueReadModel> getLeagues(@PathVariable("puuid") String puuid) {
        return ApiResponse.success(summonerQueryUseCase.getLeagues(puuid));
    }
}
