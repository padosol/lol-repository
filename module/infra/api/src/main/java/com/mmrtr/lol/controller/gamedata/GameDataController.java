package com.mmrtr.lol.controller.gamedata;

import com.mmrtr.lol.controller.gamedata.response.ChampionRotationResponse;
import com.mmrtr.lol.controller.gamedata.response.QueueInfoResponse;
import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.domain.champion.application.usecase.ChampionRotationQueryUseCase;
import com.mmrtr.lol.domain.gamedata.application.usecase.GameDataQueryUseCase;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteSummaryReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정적·준정적 게임 데이터. lol-server gamedata 의 버전·시즌·패치노트·큐·로테이션 API 를 새 경로로 이전
 * (공개 API 재설계 3.5절). 응답 본문은 lol-server 와 같다.
 */
@PublicApi
@RestController
@RequestMapping("/api/v1/game-data")
@RequiredArgsConstructor
public class GameDataController {

    private final GameDataQueryUseCase gameDataQueryUseCase;
    private final ChampionRotationQueryUseCase championRotationQueryUseCase;

    @GetMapping("/versions")
    public ApiResponse<List<VersionReadModel>> getRecentVersions() {
        return ApiResponse.success(gameDataQueryUseCase.getRecentVersions());
    }

    @GetMapping("/versions/latest")
    public ApiResponse<VersionReadModel> getLatestVersion() {
        return ApiResponse.success(gameDataQueryUseCase.getLatestVersion());
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<VersionReadModel> getVersion(@PathVariable("versionId") Long versionId) {
        return ApiResponse.success(gameDataQueryUseCase.getVersionById(versionId));
    }

    @GetMapping("/seasons")
    public ApiResponse<List<SeasonReadModel>> getSeasons() {
        return ApiResponse.success(gameDataQueryUseCase.getAllSeasons());
    }

    @GetMapping("/seasons/{seasonId}")
    public ApiResponse<SeasonReadModel> getSeason(@PathVariable("seasonId") Long seasonId) {
        return ApiResponse.success(gameDataQueryUseCase.getSeasonById(seasonId));
    }

    @GetMapping("/patch-notes")
    public ApiResponse<List<PatchNoteSummaryReadModel>> getPatchNotes() {
        return ApiResponse.success(gameDataQueryUseCase.getAllPatchNotes());
    }

    @GetMapping("/patch-notes/{versionId}")
    public ApiResponse<PatchNoteReadModel> getPatchNote(@PathVariable("versionId") String versionId) {
        return ApiResponse.success(gameDataQueryUseCase.getPatchNote(versionId));
    }

    /** 옛 {@code /api/v1/queue-tab} 은 {@code ?tab=true}. */
    @GetMapping("/queues")
    public ApiResponse<List<QueueInfoResponse>> getQueues(
            @RequestParam(name = "tab", defaultValue = "false") boolean tab
    ) {
        return ApiResponse.success(gameDataQueryUseCase.getQueues(tab).stream()
                .map(QueueInfoResponse::of)
                .toList());
    }

    @GetMapping("/champion-rotations")
    public ApiResponse<ChampionRotationResponse> getChampionRotation(
            @RequestParam("platform") String platform
    ) {
        return ApiResponse.success(
                ChampionRotationResponse.of(championRotationQueryUseCase.getChampionRotation(platform)));
    }
}
