package com.mmrtr.lol.backfill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobParametersTest {

    private final BackfillProperties props = new BackfillProperties(
            new BackfillProperties.Range(0L, 0L),
            new BackfillProperties.Chunk(1000, 2, 100),
            new BackfillProperties.Filter(16, List.of(420)),
            new BackfillProperties.Gcs("bucket"));

    @DisplayName("증분 파라미터가 없으면 BackfillProperties 로 채우고 전체 패치")
    @Test
    void resolve_withoutIncrementalParams_fallsBackToProperties() {
        // given
        JobParameters params = new JobParametersBuilder()
                .addString("factType", "match")
                .addString("runId", "r1")
                .toJobParameters();

        // when
        ExportJobParameters p = ExportJobParameters.resolve(params, props);

        // then
        assertThat(p.mode()).isEqualTo(ExportMode.BACKFILL);
        assertThat(p.season()).isEqualTo(16);
        assertThat(p.queueIds()).containsExactly(420);
        assertThat(p.allPatches()).isTrue();
    }

    @DisplayName("증분 파라미터가 있으면 properties 보다 우선한다")
    @Test
    void resolve_incrementalParams_overrideProperties() {
        // given
        JobParameters params = new JobParametersBuilder()
                .addString("mode", "incremental")
                .addLong("season", 16L)
                .addString("queueIds", "420,440")
                .addString("patches", "16.21,16.20")
                .addLong("startId", 100L)
                .addLong("endId", 200L)
                .toJobParameters();

        // when
        ExportJobParameters p = ExportJobParameters.resolve(params, props);

        // then
        assertThat(p).isEqualTo(new ExportJobParameters(ExportMode.INCREMENTAL, 16, List.of(420, 440),
                List.of("16.21", "16.20"), 100L, 200L));
    }

    @DisplayName("전체 패치면 SQL 에 allPatches=true 와 빈 문자열 하나를 넘긴다")
    @Test
    void toSqlParams_allPatches_usesPlaceholderList() {
        // given
        ExportJobParameters p = ExportJobParameters.resolve(new JobParametersBuilder().toJobParameters(), props);

        // when
        MapSqlParameterSource sql = p.toSqlParams(10L, 20L);

        // then
        assertThat(sql.getValue("allPatches")).isEqualTo(true);
        assertThat(sql.getValue("patchVersions")).isEqualTo(List.of(""));
        assertThat(sql.getValue("startId")).isEqualTo(10L);
        assertThat(sql.getValue("endId")).isEqualTo(20L);
        assertThat(sql.getValue("season")).isEqualTo(16);
        assertThat(sql.getValue("queueIds")).isEqualTo(List.of(420));
    }

    @DisplayName("패치가 지정되면 allPatches=false 와 패치 목록을 넘긴다")
    @Test
    void toSqlParams_withPatches_passesPatchList() {
        // given
        ExportJobParameters p = new ExportJobParameters(ExportMode.INCREMENTAL, 16, List.of(420),
                List.of("16.21"), 1L, 2L);

        // when
        MapSqlParameterSource sql = p.toSqlParams(1L, 2L);

        // then
        assertThat(sql.getValue("allPatches")).isEqualTo(false);
        assertThat(sql.getValue("patchVersions")).isEqualTo(List.of("16.21"));
    }

    @DisplayName("fromParam 은 null/blank 를 BACKFILL, incremental 을 대소문자 무시로 INCREMENTAL 로 본다")
    @Test
    void fromParam_variousValues_mapsMode() {
        // given / when / then
        assertThat(ExportMode.fromParam(null)).isEqualTo(ExportMode.BACKFILL);
        assertThat(ExportMode.fromParam(" ")).isEqualTo(ExportMode.BACKFILL);
        assertThat(ExportMode.fromParam("Incremental")).isEqualTo(ExportMode.INCREMENTAL);
    }
}
