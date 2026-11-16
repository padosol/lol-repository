package com.mmrtr.lol.backfill.incremental;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mmrtr.lol.backfill.BackfillFactType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatsExportControllerTest {

    private StatsExportService service;
    private StatsExportWatermarkRepository watermarks;

    @BeforeEach
    void setUp() {
        service = mock(StatsExportService.class);
        watermarks = mock(StatsExportWatermarkRepository.class);
    }

    private MockMvc mvc(TaskExecutor executor) {
        return MockMvcBuilders.standaloneSetup(new StatsExportController(service, watermarks, executor)).build();
    }

    @Test
    @DisplayName("POST /run 은 202 를 주고 틱을 제출한다")
    void run_returns202AndSubmitsTick() throws Exception {
        // given
        MockMvc mvc = mvc(new SyncTaskExecutor());

        // when
        var result = mvc.perform(post("/internal/stats-export/run"));

        // then
        result.andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(true));
        verify(service).runTickQuietly("수동");
    }

    @Test
    @DisplayName("이미 돌고 있으면 409")
    void run_busy_returns409() throws Exception {
        // given
        TaskExecutor executor = mock(TaskExecutor.class);
        doThrow(new TaskRejectedException("busy")).when(executor).execute(any(Runnable.class));

        // when
        var result = mvc(executor).perform(post("/internal/stats-export/run"));

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reason").value("already running"));
        verify(service, never()).runTickQuietly(any());
        verify(service, never()).runTick();
    }

    @Test
    @DisplayName("GET /status 는 워터마크와 마지막 보고서를 준다")
    void status_returnsWatermarksAndLastReport() throws Exception {
        // given
        when(watermarks.findAll()).thenReturn(List.of(new Watermark(BackfillFactType.MATCH, 10L, 20L, null,
                Instant.parse("2026-10-30T00:00:00Z"), 0)));
        when(service.lastReport()).thenReturn(Optional.of(new TickReport(
                false,
                List.of(new TickReport.FactReport(
                        BackfillFactType.MATCH, TickReport.FactOutcome.LOADED, 10L, 20L, 5L, "ok")),
                true,
                Instant.parse("2026-10-30T00:00:00Z"))));

        // when
        var result = mvc(new SyncTaskExecutor()).perform(get("/internal/stats-export/status"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarks[0].factType").value("MATCH"))
                .andExpect(jsonPath("$.watermarks[0].exportedUntil").value(10))
                .andExpect(jsonPath("$.lastReport.cacheEvicted").value(true))
                .andExpect(jsonPath("$.lastReport.facts[0].outcome").value("LOADED"));
    }

    @Test
    @DisplayName("GET /status 는 보고서가 없으면 lastReport 가 null")
    void status_noReport_lastReportNull() throws Exception {
        // given
        when(watermarks.findAll()).thenReturn(List.of());
        when(service.lastReport()).thenReturn(Optional.empty());

        // when
        var result = mvc(new SyncTaskExecutor()).perform(get("/internal/stats-export/status"));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.lastReport").doesNotExist());
    }
}
