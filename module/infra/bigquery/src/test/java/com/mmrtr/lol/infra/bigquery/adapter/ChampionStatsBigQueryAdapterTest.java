package com.mmrtr.lol.infra.bigquery.adapter;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import com.mmrtr.lol.domain.championstats.domain.TierFilter;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionWinRateReadModel;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** lol-server {@code ChampionStatsBigQueryAdapterTest} 이전 + 빌드 배열 파싱·patch 변환. */
class ChampionStatsBigQueryAdapterTest {

    private final BigQuery bigQuery = mock(BigQuery.class);
    private final ChampionStatsBigQueryAdapter adapter = new ChampionStatsBigQueryAdapter(
            bigQuery, new BigQueryProperties("test-project", "lol_analytics", null));

    @DisplayName("챔피언 승률 조회 시 named parameter를 BigQuery 스키마(patch_version_int, tier_bucket)에 맞춰 바인딩한다")
    @Test
    void getChampionWinRates() throws InterruptedException {
        // given
        FieldValueList row = mock(FieldValueList.class);
        given(row.get("team_position")).willReturn(value("MIDDLE"));
        given(row.get("total_games")).willReturn(value("1000"));
        given(row.get("total_wins")).willReturn(value("520"));
        given(row.get("total_win_rate")).willReturn(value("0.52"));
        givenRows(row);

        // when
        List<ChampionWinRateReadModel> rates = adapter.getChampionWinRates(13, "16.1", "KR", TierFilter.of("EMERALD+"));

        // then
        assertThat(rates).containsExactly(new ChampionWinRateReadModel("MIDDLE", 1000, 520, 0.52));

        QueryJobConfiguration job = capturedJob();
        Map<String, QueryParameterValue> params = job.getNamedParameters();
        assertThat(params).containsOnlyKeys("championId", "patch", "platform", "tierBuckets");
        assertThat(params.get("championId").getValue()).isEqualTo("13");
        assertThat(params.get("patch").getValue()).isEqualTo("1601");
        assertThat(params.get("platform").getValue()).isEqualTo("KR");
        assertThat(params.get("tierBuckets").getArrayValues())
                .extracting(QueryParameterValue::getValue)
                .containsExactlyInAnyOrder("6000", "7000", "8000", "9000", "10000");
        assertThat(job.getQuery()).contains("`lol_analytics.mv_champion_pick_stats`");
        assertThat(job.useLegacySql()).isFalse();
    }

    @DisplayName("포지션별 챔피언 통계를 Map으로 그룹핑하고 BQ가 산정한 tier 라벨을 그대로 전달한다")
    @Test
    void getChampionStatsByPosition() throws InterruptedException {
        // given
        givenRows(
                stubRateRow("TOP", "266", "0.52", "0.08", "0.05", "1500", "S+"),
                stubRateRow("TOP", "122", "0.48", "0.06", "0.03", "1200", "B"),
                stubRateRow("JUNGLE", "64", "0.51", "0.10", "0.07", "2000", "A"));

        // when
        Map<String, List<ChampionRateReadModel>> grouped =
                adapter.getChampionStatsByPosition("16.1", "KR", TierFilter.of("EMERALD+"));

        // then — 포지션 순서는 행 순서 그대로
        assertThat(grouped.keySet()).containsExactly("TOP", "JUNGLE");
        assertThat(grouped.get("TOP")).containsExactly(
                new ChampionRateReadModel(266, 0.52, 0.08, 0.05, 1500, "S+"),
                new ChampionRateReadModel(122, 0.48, 0.06, 0.03, 1200, "B"));
        assertThat(grouped.get("JUNGLE")).containsExactly(new ChampionRateReadModel(64, 0.51, 0.10, 0.07, 2000, "A"));
        assertThat(capturedJob().getQuery()).contains(
                "`lol_analytics.mv_match_count_stats`",
                "`lol_analytics.mv_champion_ban_stats`",
                "`lol_analytics.mv_champion_pick_stats`");
    }

    @DisplayName("단일 티어 필터는 해당 티어의 tier_bucket 값 하나만 바인딩한다")
    @Test
    void singleTierFilterBindsSingleBucket() throws InterruptedException {
        // given
        givenRows();

        // when
        adapter.getChampionWinRates(13, "16.1", "KR", TierFilter.of("EMERALD"));

        // then
        assertThat(capturedJob().getNamedParameters().get("tierBuckets").getArrayValues())
                .extracting(QueryParameterValue::getValue)
                .containsExactly("6000");
    }

    @DisplayName("매치업 조회 시 rank_type(TOP/BOTTOM)을 그대로 모델에 매핑하고 single UNION 쿼리로 묶는다")
    @Test
    void getChampionMatchupsMapsRankType() throws InterruptedException {
        // given
        givenRows(
                stubMatchupRow("TOP", "266", "120", "0.55", "0.12"),
                stubMatchupRow("BOTTOM", "122", "150", "0.45", "0.15"));

        // when
        List<ChampionMatchupReadModel> matchups =
                adapter.getChampionMatchups(13, "16.1", "KR", TierFilter.of("EMERALD+"), "MIDDLE");

        // then
        assertThat(matchups).containsExactly(
                ChampionMatchupReadModel.top(266, 120, 0.55, 0.12),
                ChampionMatchupReadModel.bottom(122, 150, 0.45, 0.15));

        QueryJobConfiguration job = capturedJob();
        assertThat(job.getQuery()).contains("UNION ALL");
        assertThat(job.getNamedParameters().get("position").getValue()).isEqualTo("MIDDLE");
        assertThat(job.getNamedParameters()).containsKey("championId");
    }

    @DisplayName("3코어 빌드는 BigQuery 의 JSON 배열 문자열을 아이템 ID 목록으로 바꾼다")
    @Test
    void getChampionItemBuildsParsesJsonArray() throws InterruptedException {
        // given
        FieldValueList row = mock(FieldValueList.class);
        given(row.get("item_build")).willReturn(value("[3078,3053,3065]"));
        given(row.get("games")).willReturn(value("300"));
        given(row.get("win_rate")).willReturn(value("0.5667"));
        given(row.get("pick_rate")).willReturn(value("0.3"));
        givenRows(row);

        // when
        List<ChampionItemBuildReadModel> builds =
                adapter.getChampionItemBuilds(13, "16.1", "KR", TierFilter.of("EMERALD"), "TOP");

        // then
        assertThat(builds).containsExactly(new ChampionItemBuildReadModel(List.of(3078, 3053, 3065), 300, 0.5667, 0.3));
    }

    @DisplayName("patch 는 major × 100 + minor 정수로 바꾼다")
    @Test
    void toPatchVersionInt() {
        assertThat(ChampionStatsBigQueryAdapter.toPatchVersionInt("14.24")).isEqualTo(1424);
        assertThat(ChampionStatsBigQueryAdapter.toPatchVersionInt("16.1")).isEqualTo(1601);
        assertThatThrownBy(() -> ChampionStatsBigQueryAdapter.toPatchVersionInt("16.1.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("BigQuery 쿼리가 인터럽트되면 IllegalStateException을 던지고 인터럽트 플래그를 복원한다")
    @Test
    void queryInterrupted() throws InterruptedException {
        // given
        given(bigQuery.query(any(QueryJobConfiguration.class))).willThrow(new InterruptedException("test interrupt"));
        TierFilter tierFilter = TierFilter.of("EMERALD+");

        // when & then
        assertThatThrownBy(() -> adapter.getChampionWinRates(13, "16.1", "KR", tierFilter))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    private void givenRows(FieldValueList... rows) throws InterruptedException {
        TableResult result = mock(TableResult.class);
        given(result.iterateAll()).willReturn(List.of(rows));
        given(bigQuery.query(any(QueryJobConfiguration.class))).willReturn(result);
    }

    private QueryJobConfiguration capturedJob() throws InterruptedException {
        ArgumentCaptor<QueryJobConfiguration> captor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
        verify(bigQuery).query(captor.capture());
        return captor.getValue();
    }

    private static FieldValue value(String raw) {
        return FieldValue.of(FieldValue.Attribute.PRIMITIVE, raw);
    }

    private static FieldValueList stubMatchupRow(
            String rankType, String opponentChampionId, String games, String winRate, String pickRate) {
        FieldValueList row = mock(FieldValueList.class);
        given(row.get("rank_type")).willReturn(value(rankType));
        given(row.get("opponent_champion_id")).willReturn(value(opponentChampionId));
        given(row.get("games")).willReturn(value(games));
        given(row.get("win_rate")).willReturn(value(winRate));
        given(row.get("pick_rate")).willReturn(value(pickRate));
        return row;
    }

    private static FieldValueList stubRateRow(
            String position, String championId, String winRate,
            String pickRate, String banRate, String totalGames, String tier) {
        FieldValueList row = mock(FieldValueList.class);
        given(row.get("team_position")).willReturn(value(position));
        given(row.get("champion_id")).willReturn(value(championId));
        given(row.get("win_rate")).willReturn(value(winRate));
        given(row.get("pick_rate")).willReturn(value(pickRate));
        given(row.get("ban_rate")).willReturn(value(banRate));
        given(row.get("total_games")).willReturn(value(totalGames));
        given(row.get("tier")).willReturn(value(tier));
        return row;
    }
}
