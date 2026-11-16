package com.mmrtr.lol.tools.riotmock.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchFactoryTest {

    private static final String VERSION = "16.11.712.4021";

    private final MatchFactory factory = new MatchFactory(VERSION);
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private List<String> group(int g) {
        List<String> puuids = new ArrayList<>();
        for (int n = g * 10 + 1; n <= g * 10 + 10; n++) {
            puuids.add(LabIds.puuidOf(n));
        }
        return puuids;
    }

    @Test
    @DisplayName("직렬화한 JSON 을 앱 DTO 로 다시 읽는다")
    void 직렬화한_JSON을_앱DTO로_다시_읽는다() throws Exception {
        // given
        MatchRef ref = LabIds.sharedRef(0, 3);
        List<String> puuids = group(0);

        // when
        String matchJson = mapper.writeValueAsString(factory.match(ref, puuids));
        String timelineJson = mapper.writeValueAsString(factory.timeline(ref, puuids));
        MatchDto match = mapper.readValue(matchJson, MatchDto.class);
        TimelineDto timeline = mapper.readValue(timelineJson, TimelineDto.class);

        // then
        assertThat(match.getInfo().getParticipants()).hasSize(10);
        assertThat(match.getInfo().getQueueId()).isEqualTo(420);
        assertThat(match.getMetadata().getMatchId()).isEqualTo(ref.matchId());
        assertThat(match.getMetadata().getParticipants()).isEqualTo(puuids);
        assertThat(match.getInfo().getGameVersion()).isEqualTo(VERSION);
        assertThat(match.getInfo().getGameStartTimestamp()).isEqualTo(ref.startMillis());
        assertThat(match.getInfo().getGameEndTimestamp()).isEqualTo(ref.endMillis());
        assertThat(match.getTimeline()).isNull();
        assertThat(timeline.getInfo().getFrames()).hasSize(31);
        assertThat(timeline.getMetadata().getMatchId()).isEqualTo(ref.matchId());
    }

    @Test
    @DisplayName("앱 저장 경로가 널 검사 없이 읽는 하위 객체를 모두 채운다")
    void match_저장경로가_읽는_객체는_널이_아니다() {
        // given
        MatchRef ref = LabIds.epochRef(1, 2600, 0);
        List<String> puuids = new ArrayList<>(List.of(LabIds.puuidOf(2600)));
        for (int slot = 1; slot < 10; slot++) {
            puuids.add(LabIds.npcPuuidOf(2600L * 10 + slot));
        }

        // when
        MatchDto match = factory.match(ref, puuids);
        TimelineDto timeline = factory.timeline(ref, puuids);

        // then
        assertThat(match.getInfo().getTeams()).hasSize(2).allSatisfy(t -> {
            assertThat(t.getObjectives().getBaron()).isNotNull();
            assertThat(t.getObjectives().getTower()).isNotNull();
            assertThat(t.getBans()).hasSize(5);
            assertThat(t.getFeats()).isNotNull();
        });
        for (ParticipantDto p : match.getInfo().getParticipants()) {
            assertThat(p.getChallenges()).isNotNull();
            assertThat(p.getPerks().getStatPerks()).isNotNull();
            assertThat(p.getPerks().getStyles()).hasSize(2);
            assertThat(p.getPerks().getStyles().get(0).getDescription()).isEqualTo("primaryStyle");
            assertThat(p.getPerks().getStyles().get(0).getSelections()).hasSize(4);
            assertThat(p.getPerks().getStyles().get(1).getDescription()).isEqualTo("subStyle");
            assertThat(p.getChampionId()).isPositive();
        }
        assertThat(timeline.getInfo().getFrames()).allSatisfy(f -> {
            assertThat(f.getEvents()).isNotNull();
            assertThat(f.getParticipantFrames().getP1().getParticipantId()).isEqualTo(1);
            assertThat(f.getParticipantFrames().getP10().getParticipantId()).isEqualTo(10);
            assertThat(f.getParticipantFrames().getP5().getChampionStats()).isNotNull();
            assertThat(f.getParticipantFrames().getP5().getDamageStats()).isNotNull();
            assertThat(f.getParticipantFrames().getP5().getPosition()).isNotNull();
        });
        assertThat(timeline.getInfo().getFrames().get(30).getTimestamp()).isEqualTo(1_800_000);
    }

    @Test
    @DisplayName("같은 입력은 같은 JSON 을 만든다")
    void match_같은입력은_같은JSON() throws Exception {
        // given
        MatchRef ref = LabIds.personalRef(5, 4);
        List<String> puuids = group(0);

        // when
        String first = mapper.writeValueAsString(factory.match(ref, puuids));
        String second = mapper.writeValueAsString(new MatchFactory(VERSION).match(ref, puuids));

        // then
        assertThat(second).isEqualTo(first);
    }
}
