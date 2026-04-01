package com.mmrtr.lol.controller.admin;

import com.mmrtr.lol.domain.league.application.usecase.CollectTierDataUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tier-collection")
@RequiredArgsConstructor
public class AdminTierCollectionController {

    private final CollectTierDataUseCase collectTierDataUseCase;
    private final AdminAccessKeyProvider adminAccessKeyProvider;

    @PostMapping("/collect")
    public ResponseEntity<Void> collect(
            @RequestParam String accessKey,
            @RequestParam(defaultValue = "KR") String platform) {
        if (!adminAccessKeyProvider.validate(accessKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        collectTierDataUseCase.execute(platform);
        return ResponseEntity.accepted().build();
    }
}
