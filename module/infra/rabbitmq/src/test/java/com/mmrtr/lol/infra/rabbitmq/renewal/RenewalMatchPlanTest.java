package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchPlan.Item;
import com.mmrtr.lol.support.riot.RiotMethod;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalMatchPlanTest {

    private static final List<String> TWENTY = IntStream.rangeClosed(1, 20).mapToObj(i -> "m" + i).toList();

    @Test
    @DisplayName("새 매치 20개면 모두 매치 대상이고, 타임라인은 id 앞 3개만 받는다")
    void timelinesOnlyForFirstThreeIds() {
        // when
        RenewalMatchPlan plan = RenewalMatchPlan.of(TWENTY, Set.of(), Set.of(), Set.of());

        // then
        assertThat(plan.items()).hasSize(20);
        assertThat(plan.items()).extracting(Item::matchId).containsExactlyElementsOf(TWENTY);
        assertThat(plan.items()).allMatch(Item::match);
        assertThat(plan.items().stream().filter(Item::timeline).map(Item::matchId))
                .containsExactly("m1", "m2", "m3");
        assertThat(plan.matchTargets()).containsExactlyElementsOf(TWENTY);
    }

    @Test
    @DisplayName("앞 3개 중 매치는 DB 에 있고 타임라인만 없는 것은 타임라인만 받는 항목이 된다")
    void existingMatchMissingTimelineBecomesTimelineOnly() {
        // when
        RenewalMatchPlan plan = RenewalMatchPlan.of(List.of("m1", "m2", "m3"), Set.of("m2"), Set.of(), Set.of());

        // then
        assertThat(plan.items()).containsExactly(
                new Item("m1", true, true), new Item("m2", false, true), new Item("m3", true, true));
        assertThat(plan.matchTargets()).containsExactly("m1", "m3");
    }

    @Test
    @DisplayName("앞 3개의 타임라인이 모두 DB 에 있으면 4번째로 채우지 않는다")
    void doesNotFillFourthTimeline() {
        // when
        RenewalMatchPlan plan = RenewalMatchPlan.of(TWENTY, Set.of("m1", "m2", "m3"), Set.of(),
                Set.of("m1", "m2", "m3"));

        // then
        assertThat(plan.items()).noneMatch(Item::timeline);
        assertThat(plan.items()).extracting(Item::matchId).containsExactlyElementsOf(TWENTY.subList(3, 20));
    }

    @Test
    @DisplayName("DB 에 있거나 계속 실패해 빠진 매치는 매치 대상이 아니다. 빠진 매치는 타임라인도 받지 않는다")
    void excludedAndExistingAreNotMatchTargets() {
        // when: m1 은 빠짐, m2 는 매치·타임라인 모두 DB 에 있음, m4 는 매치만 DB 에 있음 (앞 3개 밖)
        RenewalMatchPlan plan = RenewalMatchPlan.of(List.of("m1", "m2", "m3", "m4", "m5"),
                Set.of("m2", "m4"), Set.of("m1"), Set.of("m2"));

        // then
        assertThat(plan.matchTargets()).containsExactly("m3", "m5");
        assertThat(plan.items()).containsExactly(new Item("m3", true, true), new Item("m5", true, false));
    }

    @Test
    @DisplayName("항목의 비용은 매치면 MATCH 1, 타임라인이면 MATCH_TIMELINE 1 이다")
    void itemCostCountsRequestedCallsOnly() {
        // when // then
        assertThat(new Item("m1", true, true).cost())
                .isEqualTo(Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1));
        assertThat(new Item("m1", true, false).cost()).isEqualTo(Map.of(RiotMethod.MATCH, 1));
        assertThat(new Item("m1", false, true).cost()).isEqualTo(Map.of(RiotMethod.MATCH_TIMELINE, 1));
    }

    @Test
    @DisplayName("id 가 없으면 항목도 대상도 없다")
    void emptyIdsMakeEmptyPlan() {
        // when
        RenewalMatchPlan plan = RenewalMatchPlan.of(List.of(), Set.of(), Set.of(), Set.of());

        // then
        assertThat(plan.items()).isEmpty();
        assertThat(plan.matchTargets()).isEmpty();
    }
}
