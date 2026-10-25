package com.mmrtr.lol.controller.spectator;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.domain.spectator.application.usecase.ActiveGameQueryUseCase;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소환사의 진행 중인 게임. lol-server summoner 모듈 {@code SpectatorController} 의 새 경로를 이전 (공개 API 재설계 3.1절).
 * 게임 중이 아니면 200 + {@code data: null} (lol-server 와 같다).
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/summoners")
@RequiredArgsConstructor
public class ActiveGameController {

    private final ActiveGameQueryUseCase activeGameQueryUseCase;

    @GetMapping("/{puuid}/active-game")
    public ApiResponse<ActiveGameReadModel> getActiveGame(@PathVariable("puuid") String puuid) {
        return ApiResponse.success(activeGameQueryUseCase.getActiveGame(puuid).orElse(null));
    }
}
