package com.mmrtr.lol.domain.match.readmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 매치 타임라인 — 참가자 번호별 아이템 구매·스킬 레벨업 순서. 필드·키 순서가 lol-server {@code TimelineResponse} 와 같다.
 * 매치 응답의 참가자 {@code itemSeq}·{@code skillSeq} 도 같은 규칙으로 만든다.
 */
public record MatchTimelineReadModel(Map<Integer, ParticipantTimeline> participants) {

    private static final String ITEM_PURCHASED = "ITEM_PURCHASED";
    private static final String SKILL_LEVEL_UP = "SKILL_LEVEL_UP";

    public record ParticipantTimeline(List<ItemSeq> itemSeq, List<SkillSeq> skillSeq) {
    }

    public record ItemSeq(int itemId, long minute, String type) {
    }

    public record SkillSeq(int skillSlot, long minute, String type) {
    }

    /**
     * 저장된 타임라인 이벤트 한 건 ({@code timeline_event_frame}).
     *
     * @param timestamp 이벤트가 속한 프레임의 시각(ms). lol-server 와 같이 이 값으로 분을 센다
     */
    public record Event(String matchId, int participantId, String type, Integer itemId, Integer skillSlot,
                        long timestamp) {
    }

    /**
     * 참가자별로 묶는다. 키는 아이템 구매가 처음 나온 순서, 그다음 스킬만 있는 참가자 순 (lol-server 와 같다).
     *
     * @param events 프레임 시각·이벤트 순
     */
    public static MatchTimelineReadModel from(List<Event> events) {
        Map<Integer, List<ItemSeq>> itemsByParticipant = new LinkedHashMap<>();
        Map<Integer, List<SkillSeq>> skillsByParticipant = new LinkedHashMap<>();
        for (Event event : events) {
            if (ITEM_PURCHASED.equalsIgnoreCase(event.type())) {
                itemsByParticipant.computeIfAbsent(event.participantId(), id -> new ArrayList<>())
                        .add(new ItemSeq(orZero(event.itemId()), toMinute(event.timestamp()), event.type()));
            } else if (SKILL_LEVEL_UP.equals(event.type())) {
                skillsByParticipant.computeIfAbsent(event.participantId(), id -> new ArrayList<>())
                        .add(new SkillSeq(orZero(event.skillSlot()), toMinute(event.timestamp()), event.type()));
            }
        }

        Set<Integer> participantIds = new LinkedHashSet<>(itemsByParticipant.keySet());
        participantIds.addAll(skillsByParticipant.keySet());
        Map<Integer, ParticipantTimeline> participants = new LinkedHashMap<>();
        for (Integer participantId : participantIds) {
            participants.put(participantId, new ParticipantTimeline(
                    itemsByParticipant.getOrDefault(participantId, List.of()),
                    skillsByParticipant.getOrDefault(participantId, List.of())));
        }
        return new MatchTimelineReadModel(participants);
    }

    private static long toMinute(long timestamp) {
        return timestamp / 1000 / 60;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
