package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchPlan.Item;
import com.mmrtr.lol.support.riot.RiotMethod;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalMatchPlanTest {

    private static final List<String> TWENTY = IntStream.rangeClosed(1, 20).mapToObj(i -> "m" + i).toList();

    @Test
    @DisplayName("모든 id 가 매치 항목이고, 타임라인은 앞 3개만 받는다")
    void timelinesOnlyForFirstThreeIds() {
        // when
        List<Item> items = RenewalMatchPlan.items(TWENTY);

        // then
        assertThat(items).extracting(Item::matchId).containsExactlyElementsOf(TWENTY);
        assertThat(items).filteredOn(Item::timeline).extracting(Item::matchId).containsExactly("m1", "m2", "m3");
    }

    @Test
    @DisplayName("항목의 비용은 MATCH 1 이고, 타임라인이면 MATCH_TIMELINE 1 이 더해진다")
    void itemCostCountsRequestedCalls() {
        // when & then
        assertThat(new Item("m1", true).cost()).isEqualTo(Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1));
        assertThat(new Item("m4", false).cost()).isEqualTo(Map.of(RiotMethod.MATCH, 1));
    }

    @Test
    @DisplayName("id 가 없으면 항목도 없다")
    void emptyIdsMakeNoItems() {
        // when & then
        assertThat(RenewalMatchPlan.items(List.of())).isEmpty();
    }
}
