package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.readmodel.ChallengesDto;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamInfo;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ItemSeq;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.SkillSeq;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectiveDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectivesDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.StatValue;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.Style;
import com.mmrtr.lol.domain.match.readmodel.PerkStatsDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStyleDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStyleSelectionDto;
import com.mmrtr.lol.domain.match.readmodel.PerksDto;
import com.mmrtr.lol.domain.match.readmodel.TeamDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.EventsTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.FramesTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.InfoTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.redis.adapter.view.MatchCacheViewMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** {@link MatchCacheWriteAdapter} 가 쓴 키·값을 그대로 읽는지 — 쓰기 view JSON 을 읽기 모델로 왕복한다. */
class MatchCacheReadAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
    private final MatchCacheReadAdapter adapter = new MatchCacheReadAdapter(redisTemplate, objectMapper);

    MatchCacheReadAdapterTest() {
        given(redisTemplate.opsForValue()).willReturn(values);
        given(redisTemplate.opsForZSet()).willReturn(zSet);
    }

    @DisplayName("최근 매치 ID 는 match:ids:v1:{puuid} ZSET 의 점수 역순, 없거나 Redis 장애면 빈 목록")
    @Test
    void findRecentMatchIds_zset_readsReverseRange() {
        // given
        given(zSet.reverseRange("match:ids:v1:p1", 0, -1)).willReturn(new LinkedHashSet<>(List.of("KR_3", "KR_1")));
        given(zSet.reverseRange("match:ids:v1:down", 0, -1)).willThrow(new RedisConnectionFailureException("down"));

        // when & then
        assertThat(adapter.findRecentMatchIds("p1")).containsExactly("KR_3", "KR_1");
        assertThat(adapter.findRecentMatchIds("none")).isEmpty();
        assertThat(adapter.findRecentMatchIds("down")).isEmpty();
    }

    @DisplayName("쓰기 view JSON(match:v1:{matchId})을 읽기 모델로 그대로 읽는다 — 없는 키는 결과에 없다")
    @Test
    void findGames_writtenView_roundTripsToReadModel() throws Exception {
        // given
        String json = objectMapper.writeValueAsString(new MatchCacheViewMapper().toView(match(), timeline()));
        given(values.multiGet(List.of("match:v1:KR_1", "match:v1:KR_2"))).willReturn(Arrays.asList(json, null));

        // when
        Map<String, GameReadModel> games = adapter.findGames(List.of("KR_1", "KR_2"));

        // then
        assertThat(games).containsOnlyKeys("KR_1");
        GameReadModel game = games.get("KR_1");
        assertThat(game.gameInfoData().matchId()).isEqualTo("KR_1");
        assertThat(game.gameInfoData().gameEndTimestamp()).isEqualTo(1700001800000L);
        assertThat(game.gameInfoData().gameMode()).isEqualTo("CLASSIC");
        assertThat(game.gameInfoData().averageTier()).isNull();

        ParticipantReadModel participant = game.participantData().get(0);
        assertThat(participant.riotIdGameName()).isEqualTo("Hide on bush");
        assertThat(participant.timeCCingOthers()).isEqualTo(25);
        assertThat(participant.kda()).isEqualTo(7.5);
        assertThat(participant.statValue()).isEqualTo(new StatValue(5002, 5008, 5005));
        assertThat(participant.style()).isEqualTo(new Style(8100, 8112, 0, 0, 0, 8300, 8304, 0));
        assertThat(participant.itemSeq()).containsExactly(new ItemSeq(3157, 12, "ITEM_PURCHASED"));
        assertThat(participant.skillSeq()).containsExactly(new SkillSeq(1, 1, "SKILL_LEVEL_UP"));

        assertThat(game.teamInfoData().blueTeam().championKills()).isEqualTo(25);
        assertThat(game.teamInfoData().blueTeam().goldTimeline()).isNull();
        // 쓰기 측은 Riot 응답에 없는 팀도 빈 팀으로 쓴다
        assertThat(game.teamInfoData().redTeam()).isEqualTo(new TeamInfo(0, false, 0, 0, 0, 0, 0, null, null));
    }

    @DisplayName("깨진 값은 건너뛰고, Redis 장애면 빈 결과")
    @Test
    void findGames_brokenValueOrRedisDown_returnsWhatCanBeRead() {
        // given
        given(values.multiGet(List.of("match:v1:BROKEN"))).willReturn(List.of("{not json"));

        // when & then
        assertThat(adapter.findGames(List.of("BROKEN"))).isEmpty();
        given(values.multiGet(anyList())).willThrow(new RedisConnectionFailureException("down"));
        assertThat(adapter.findGames(List.of("KR_1"))).isEmpty();
        assertThat(adapter.findGames(List.of())).isEmpty();
    }

    private static MatchDto match() {
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId("KR_1");
        metadata.setDataVersion("2");

        ParticipantDto participant = new ParticipantDto();
        participant.setPuuid("p1");
        participant.setParticipantId(1);
        participant.setTeamId(100);
        participant.setWin(true);
        participant.setRiotIdGameName("Hide on bush");
        participant.setTimeCCingOthers(25);
        ChallengesDto challenges = new ChallengesDto();
        challenges.setKda(7.5);
        participant.setChallenges(challenges);
        participant.setPerks(perks());

        ObjectiveDto championKills = new ObjectiveDto();
        championKills.setKills(25);
        ObjectivesDto objectives = new ObjectivesDto();
        objectives.setChampion(championKills);
        TeamDto blueTeam = new TeamDto();
        blueTeam.setTeamId(100);
        blueTeam.setWin(true);
        blueTeam.setObjectives(objectives);

        InfoDto info = new InfoDto();
        info.setGameCreation(1700000000000L);
        info.setGameEndTimestamp(1700001800000L);
        info.setGameMode("CLASSIC");
        info.setQueueId(420);
        info.setParticipants(List.of(participant));
        info.setTeams(List.of(blueTeam));

        MatchDto match = new MatchDto();
        match.setMetadata(metadata);
        match.setInfo(info);
        return match;
    }

    private static PerksDto perks() {
        PerkStatsDto statPerks = new PerkStatsDto();
        statPerks.setDefense(5002);
        statPerks.setFlex(5008);
        statPerks.setOffense(5005);
        PerksDto perks = new PerksDto();
        perks.setStatPerks(statPerks);
        perks.setStyles(List.of(style(8100, 8112), style(8300, 8304)));
        return perks;
    }

    private static PerkStyleDto style(int styleId, int firstPerk) {
        PerkStyleSelectionDto selection = new PerkStyleSelectionDto();
        selection.setPerk(firstPerk);
        PerkStyleDto style = new PerkStyleDto();
        style.setStyle(styleId);
        style.setSelections(List.of(selection));
        return style;
    }

    private static TimelineDto timeline() {
        EventsTimeLineDto item = new EventsTimeLineDto();
        item.setType("ITEM_PURCHASED");
        item.setParticipantId(1);
        item.setItemId(3157);
        item.setTimestamp(12 * 60_000 + 5);
        EventsTimeLineDto skill = new EventsTimeLineDto();
        skill.setType("SKILL_LEVEL_UP");
        skill.setParticipantId(1);
        skill.setSkillSlot(1);
        skill.setTimestamp(60_000);
        FramesTimeLineDto frame = new FramesTimeLineDto();
        frame.setEvents(List.of(item, skill));
        InfoTimeLineDto info = new InfoTimeLineDto();
        info.setFrames(List.of(frame));
        TimelineDto timeline = new TimelineDto();
        timeline.setInfo(info);
        return timeline;
    }
}
