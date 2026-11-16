package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.support.riot.RiotMethod;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 갱신 매치 단계가 받을 항목. 모든 항목은 매치를 받고, 앞 {@value #TIMELINE_COUNT}개(최신순)만 타임라인까지 받는다.
 */
final class RenewalMatchPlan {

    static final int TIMELINE_COUNT = 3;

    private RenewalMatchPlan() {
    }

    /** ids 순서대로 Item. 앞 {@value #TIMELINE_COUNT}개만 timeline=true. ids 는 이미 거르고 중복을 뺀 것이다. */
    static List<Item> items(List<String> ids) {
        List<Item> items = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            items.add(new Item(ids.get(i), i < TIMELINE_COUNT));
        }
        return List.copyOf(items);
    }

    /** 받을 id 하나. timeline 이면 타임라인도 받는다. */
    record Item(String matchId, boolean timeline) {

        /** 이 항목에 필요한 허가 (MATCH 1, timeline 이면 MATCH_TIMELINE 1 더). */
        Map<RiotMethod, Integer> cost() {
            Map<RiotMethod, Integer> cost = new EnumMap<>(RiotMethod.class);
            cost.put(RiotMethod.MATCH, 1);
            if (timeline) {
                cost.put(RiotMethod.MATCH_TIMELINE, 1);
            }
            return cost;
        }
    }
}
