package com.mmrtr.lol.controller.spectator;

import com.mmrtr.lol.controller.spectator.response.ActiveGameResponse;
import com.mmrtr.lol.domain.spectator.service.SpectatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/riot/{region}/spectator")
@RequiredArgsConstructor
public class SpectatorController {

    private final SpectatorService spectatorService;

    @GetMapping("/active-games/by-puuid/{puuid}")
    public ResponseEntity<ActiveGameResponse> getActiveGame(
            @PathVariable("region") String region,
            @PathVariable("puuid") String puuid
    ) {
        return spectatorService.getActiveGameByPuuid(region, puuid)
                .map(ActiveGameResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
