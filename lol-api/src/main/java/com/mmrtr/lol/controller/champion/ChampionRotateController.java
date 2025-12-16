package com.mmrtr.lol.controller.champion;

import com.mmrtr.lol.controller.champion.response.ChampionRotateResponse;
import com.mmrtr.lol.domain.champion.service.ChampionRotateService;
import com.mmrtr.lol.riot.dto.champion.ChampionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/riot/{region}/champion-rotate")
@RequiredArgsConstructor
public class ChampionRotateController {

    private final ChampionRotateService championRotateService;

    @GetMapping
    public ResponseEntity<ChampionRotateResponse> getChampionRotate(
            @PathVariable("region") String region
    ) {
        ChampionInfo championRotate = championRotateService.getChampionRotate(region);

        return ResponseEntity.ok(ChampionRotateResponse.of(championRotate));
    }
}
