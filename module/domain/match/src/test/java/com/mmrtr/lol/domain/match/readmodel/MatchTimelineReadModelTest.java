package com.mmrtr.lol.domain.match.readmodel;

import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ItemSeq;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ParticipantTimeline;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.SkillSeq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTimelineReadModelTest {

    @DisplayName("참가자별 아이템 구매·스킬 레벨업 순서, 분은 프레임 시각(ms)을 내림한 값")
    @Test
    void from_events_groupsByParticipantInEventOrder() {
        // given
        List<Event> events = List.of(
                new Event("KR_1", 2, "ITEM_PURCHASED", 1055, null, 59999),
                new Event("KR_1", 2, "SKILL_LEVEL_UP", null, 1, 60000),
                new Event("KR_1", 2, "ITEM_PURCHASED", 2003, null, 125000),
                new Event("KR_1", 1, "ITEM_PURCHASED", null, null, 180000));

        // when
        MatchTimelineReadModel timeline = MatchTimelineReadModel.from(events);

        // then
        assertThat(timeline.participants()).containsEntry(2, new ParticipantTimeline(
                List.of(new ItemSeq(1055, 0, "ITEM_PURCHASED"), new ItemSeq(2003, 2, "ITEM_PURCHASED")),
                List.of(new SkillSeq(1, 1, "SKILL_LEVEL_UP"))));
        assertThat(timeline.participants().get(1).itemSeq()).containsExactly(new ItemSeq(0, 3, "ITEM_PURCHASED"));
        assertThat(timeline.participants().get(1).skillSeq()).isEmpty();
    }

    @DisplayName("키 순서는 아이템 구매가 처음 나온 참가자 순, 그다음 스킬만 있는 참가자 (lol-server 와 같다)")
    @Test
    void from_events_ordersKeysByFirstItemThenSkillOnly() {
        // given
        List<Event> events = List.of(
                new Event("KR_1", 3, "SKILL_LEVEL_UP", null, 1, 0),
                new Event("KR_1", 5, "ITEM_PURCHASED", 1055, null, 0),
                new Event("KR_1", 1, "ITEM_PURCHASED", 1055, null, 0),
                new Event("KR_1", 7, "ITEM_SOLD", 1055, null, 0));

        // when
        MatchTimelineReadModel timeline = MatchTimelineReadModel.from(events);

        // then
        assertThat(timeline.participants().keySet()).containsExactly(5, 1, 3);
    }

    @DisplayName("이벤트가 없으면 빈 participants")
    @Test
    void from_noEvents_returnsEmpty() {
        // when & then
        assertThat(MatchTimelineReadModel.from(List.of()).participants()).isEmpty();
    }
}
