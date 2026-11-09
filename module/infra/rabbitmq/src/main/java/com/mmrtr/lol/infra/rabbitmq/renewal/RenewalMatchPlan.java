package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.support.riot.RiotMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 갱신 매치 단계가 받을 것. 매치 대상은 DB 에 없고 계속 실패해 빠지지 않은 매치다.
 * 타임라인 대상은 id 앞 {@value #TIMELINE_COUNT}개(최신순) 중 타임라인이 DB 에 없는 것이다. 4번째로 채우지 않는다.
 * 빠진 매치는 백필 순위로 통째로 넘기므로 타임라인 대상에서도 뺀다.
 *
 * @param items        매치 대상이거나 타임라인 대상인 id. id 순서(최신순)다
 * @param matchTargets 매치 대상. 레코드에 기록하고, 모두 저장되면 매치 비트를 켠다
 */
record RenewalMatchPlan(List<Item> items, Set<String> matchTargets) {

    static final int TIMELINE_COUNT = 3;

    static RenewalMatchPlan of(List<String> ids, Set<String> existing, Set<String> excluded,
                               Set<String> withTimeline) {
        Set<String> timelineTargets = new LinkedHashSet<>(ids.subList(0, Math.min(TIMELINE_COUNT, ids.size())));
        timelineTargets.removeAll(withTimeline);
        timelineTargets.removeAll(excluded);

        List<Item> items = new ArrayList<>();
        Set<String> matchTargets = new LinkedHashSet<>();
        for (String matchId : new LinkedHashSet<>(ids)) {
            boolean match = !existing.contains(matchId) && !excluded.contains(matchId);
            boolean timeline = timelineTargets.contains(matchId);
            if (match) {
                matchTargets.add(matchId);
            }
            if (match || timeline) {
                items.add(new Item(matchId, match, timeline));
            }
        }
        return new RenewalMatchPlan(List.copyOf(items), Collections.unmodifiableSet(matchTargets));
    }

    /**
     * 받을 id 하나. match 면 매치를, timeline 이면 타임라인을 받는다.
     * 매치는 DB 에 있고 타임라인만 없으면 {@code match=false, timeline=true} 다.
     */
    record Item(String matchId, boolean match, boolean timeline) {

        /** 이 항목에 필요한 허가 (match → MATCH 1, timeline → MATCH_TIMELINE 1). */
        Map<RiotMethod, Integer> cost() {
            Map<RiotMethod, Integer> cost = new EnumMap<>(RiotMethod.class);
            if (match) {
                cost.put(RiotMethod.MATCH, 1);
            }
            if (timeline) {
                cost.put(RiotMethod.MATCH_TIMELINE, 1);
            }
            return cost;
        }

        /** 매치는 받지 않고 타임라인만 받는 항목. */
        Item timelineOnly() {
            return new Item(matchId, false, true);
        }
    }
}
