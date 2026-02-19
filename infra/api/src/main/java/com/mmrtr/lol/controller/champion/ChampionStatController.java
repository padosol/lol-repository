package com.mmrtr.lol.controller.champion;

import com.mmrtr.lol.common.type.Position;
import com.mmrtr.lol.common.type.TierGroup;
import com.mmrtr.lol.controller.champion.response.*;
import com.mmrtr.lol.domain.champion_stat.service.usecase.ChampionStatQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/riot/champions/{championId}/stats")
@RequiredArgsConstructor
public class ChampionStatController {

    private final ChampionStatQueryUseCase championStatQueryUseCase;

    @GetMapping("/summary")
    public ResponseEntity<List<ChampionStatSummaryResponse>> getSummary(
            @PathVariable int championId,
            @RequestParam Position position,
            @RequestParam int season,
            @RequestParam TierGroup tierGroup,
            @RequestParam String platform,
            @RequestParam int queueId,
            @RequestParam String patch) {
        return ResponseEntity.ok(championStatQueryUseCase.getSummaries(championId, position.name(), season, tierGroup.name(), platform, queueId, patch)
                .stream().map(ChampionStatSummaryResponse::of).toList());
    }

    @GetMapping("/runes")
    public ResponseEntity<List<ChampionRuneStatResponse>> getRuneStats(
            @PathVariable int championId,
            @RequestParam Position position,
            @RequestParam int season,
            @RequestParam TierGroup tierGroup,
            @RequestParam String platform,
            @RequestParam int queueId,
            @RequestParam String patch) {
        return ResponseEntity.ok(championStatQueryUseCase.getRuneStats(championId, position.name(), season, tierGroup.name(), platform, queueId, patch)
                .stream().map(ChampionRuneStatResponse::of).toList());
    }

    @GetMapping("/spells")
    public ResponseEntity<List<ChampionSpellStatResponse>> getSpellStats(
            @PathVariable int championId,
            @RequestParam Position position,
            @RequestParam int season,
            @RequestParam TierGroup tierGroup,
            @RequestParam String platform,
            @RequestParam int queueId,
            @RequestParam String patch) {
        return ResponseEntity.ok(championStatQueryUseCase.getSpellStats(championId, position.name(), season, tierGroup.name(), platform, queueId, patch)
                .stream().map(ChampionSpellStatResponse::of).toList());
    }

    @GetMapping("/skills")
    public ResponseEntity<List<ChampionSkillStatResponse>> getSkillStats(
            @PathVariable int championId,
            @RequestParam Position position,
            @RequestParam int season,
            @RequestParam TierGroup tierGroup,
            @RequestParam String platform,
            @RequestParam int queueId,
            @RequestParam String patch) {
        return ResponseEntity.ok(championStatQueryUseCase.getSkillStats(championId, position.name(), season, tierGroup.name(), platform, queueId, patch)
                .stream().map(ChampionSkillStatResponse::of).toList());
    }

    @GetMapping("/items")
    public ResponseEntity<List<ChampionItemStatResponse>> getItemStats(
            @PathVariable int championId,
            @RequestParam Position position,
            @RequestParam int season,
            @RequestParam TierGroup tierGroup,
            @RequestParam String platform,
            @RequestParam int queueId,
            @RequestParam String patch) {
        return ResponseEntity.ok(championStatQueryUseCase.getItemStats(championId, position.name(), season, tierGroup.name(), platform, queueId, patch)
                .stream().map(ChampionItemStatResponse::of).toList());
    }

    @GetMapping("/matchups")
    public ResponseEntity<List<ChampionMatchupStatResponse>> getMatchupStats(
            @PathVariable int championId,
            @RequestParam Position position,
            @RequestParam int season,
            @RequestParam TierGroup tierGroup,
            @RequestParam String platform,
            @RequestParam int queueId,
            @RequestParam String patch) {
        return ResponseEntity.ok(championStatQueryUseCase.getMatchupStats(championId, position.name(), season, tierGroup.name(), platform, queueId, patch)
                .stream().map(ChampionMatchupStatResponse::of).toList());
    }
}
