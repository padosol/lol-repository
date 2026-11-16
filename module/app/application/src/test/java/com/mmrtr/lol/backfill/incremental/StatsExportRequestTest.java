package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.ExportJobParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatsExportRequestTest {

    private static StatsExportRequest request(int epoch) {
        return new StatsExportRequest(BackfillFactType.MATCH_BAN, 100, 200, 16, List.of("16.21"), List.of(420), epoch);
    }

    @Test
    @DisplayName("exportEpoch 는 identifying 파라미터라 epoch 가 다르면 다른 JobInstance 가 된다")
    void toJobParameters_exportEpochIsIdentifying() {
        // when
        JobParameters epoch0 = request(0).toJobParameters();
        JobParameters epoch1 = request(1).toJobParameters();

        // then
        assertThat(epoch1.getLong(ExportJobParameters.KEY_EXPORT_EPOCH)).isEqualTo(1L);
        assertThat(epoch1.getParameters().get(ExportJobParameters.KEY_EXPORT_EPOCH).isIdentifying()).isTrue();
        assertThat(epoch0.getIdentifyingParameters()).isNotEqualTo(epoch1.getIdentifyingParameters());
    }

    @Test
    @DisplayName("runId 는 epoch 와 무관하게 범위로만 정해진다 (BigQuery jobId 도 그대로)")
    void runId_excludesEpoch() {
        // when
        String runId0 = request(0).runId();
        String runId3 = request(3).runId();

        // then
        assertThat(runId0).isEqualTo("inc-match-ban-100-200").isEqualTo(runId3);
    }

    @Test
    @DisplayName("대상 패치가 비면 '전체 패치' 로 해석되므로 요청을 만들 수 없다")
    void create_emptyPatchVersions_throws() {
        // when / then
        assertThatThrownBy(() -> new StatsExportRequest(
                BackfillFactType.MATCH, 100, 200, 16, List.of(), List.of(420), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatsExportRequest(
                BackfillFactType.MATCH, 100, 200, 16, null, List.of(420), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("큐 목록이 비면 요청을 만들 수 없다")
    void create_emptyQueueIds_throws() {
        // when / then
        assertThatThrownBy(() -> new StatsExportRequest(
                BackfillFactType.MATCH, 100, 200, 16, List.of("16.21"), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatsExportRequest(
                BackfillFactType.MATCH, 100, 200, 16, List.of("16.21"), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
