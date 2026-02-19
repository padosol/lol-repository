package com.mmrtr.lol.controller.admin;

import com.mmrtr.lol.controller.admin.request.ItemMetadataRequest;
import com.mmrtr.lol.controller.admin.response.ChampionStatAggregateResponse;
import com.mmrtr.lol.domain.champion_stat.domain.ItemMetadata;
import com.mmrtr.lol.domain.champion_stat.service.usecase.ItemMetadataUseCase;
import com.mmrtr.lol.domain.champion_stat.service.usecase.TriggerChampionStatAggregationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/champion-stats")
@RequiredArgsConstructor
public class ChampionStatAdminController {

    private final TriggerChampionStatAggregationUseCase triggerChampionStatAggregationUseCase;
    private final ItemMetadataUseCase itemMetadataUseCase;

    @PostMapping("/aggregate")
    public ResponseEntity<ChampionStatAggregateResponse> aggregate(
            @RequestParam int season,
            @RequestParam int queueId) {
        triggerChampionStatAggregationUseCase.execute(season, queueId);
        return ResponseEntity.ok(ChampionStatAggregateResponse.of());
    }

    @PostMapping("/item-metadata")
    public ResponseEntity<ItemMetadata> saveItemMetadata(@RequestBody ItemMetadataRequest request) {
        ItemMetadata itemMetadata = ItemMetadata.builder()
                .itemId(request.itemId())
                .itemName(request.itemName())
                .itemCategory(request.itemCategory().name())
                .gameVersion(request.gameVersion())
                .build();
        return ResponseEntity.ok(itemMetadataUseCase.save(itemMetadata));
    }

    @PostMapping("/item-metadata/bulk")
    public ResponseEntity<List<ItemMetadata>> saveItemMetadataBulk(@RequestBody List<ItemMetadataRequest> requests) {
        List<ItemMetadata> metadataList = requests.stream()
                .map(req -> ItemMetadata.builder()
                        .itemId(req.itemId())
                        .itemName(req.itemName())
                        .itemCategory(req.itemCategory().name())
                        .gameVersion(req.gameVersion())
                        .build())
                .toList();
        return ResponseEntity.ok(itemMetadataUseCase.saveAll(metadataList));
    }

    @GetMapping("/item-metadata")
    public ResponseEntity<List<ItemMetadata>> getItemMetadata() {
        return ResponseEntity.ok(itemMetadataUseCase.findAll());
    }
}
