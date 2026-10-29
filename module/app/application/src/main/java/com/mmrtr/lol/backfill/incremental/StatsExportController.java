package com.mmrtr.lol.backfill.incremental;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 전용: 증분 적재 틱을 수동 실행하고 상태를 조회한다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/stats-export")
public class StatsExportController {

    private final StatsExportService service;
    private final StatsExportWatermarkRepository watermarkRepository;
    private final TaskExecutor triggerExecutor;

    public StatsExportController(
            StatsExportService service,
            StatsExportWatermarkRepository watermarkRepository,
            @Qualifier("statsExportTriggerExecutor") TaskExecutor triggerExecutor
    ) {
        this.service = service;
        this.watermarkRepository = watermarkRepository;
        this.triggerExecutor = triggerExecutor;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run() {
        try {
            triggerExecutor.execute(this::runQuietly);
        } catch (TaskRejectedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("accepted", false, "reason", "already running"));
        }
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    private void runQuietly() {
        try {
            service.runTick();
        } catch (RuntimeException e) {
            log.error("수동 통계 적재 틱 실패", e);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("watermarks", watermarkRepository.findAll());
        body.put("lastReport", service.lastReport().orElse(null));
        return ResponseEntity.ok(body);
    }
}
