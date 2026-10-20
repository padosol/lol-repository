package com.mmrtr.lol.controller.match;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.Team;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamInfo;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ItemSeq;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ParticipantTimeline;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.SkillSeq;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.Item;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.StatValue;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.Style;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lol-server {@code MatchControllerTest} 의 응답 필드 정의를 그대로 옮겨 응답이 lol-server 와 같은지 검증한다.
 * responseFields 는 문서화되지 않은 필드가 있거나 빠지면 실패한다.
 *
 * <p>단건 조회는 lol-server 가 하위 섹션만 문서화했으므로, 같은 {@code GameResponse} 를 전부 문서화한
 * 소환사별 전적 테스트({@code match-summoner-get-list})의 필드 정의를 {@code data.} 아래에 옮겨 검증한다.
 * 조립 규칙(팀 골드 합·아레나 정렬·아이템/스킬 순서)은 {@code GameReadModelTest} 가 검증한다.
 */
class MatchControllerTest extends RestDocsSupport {

    private final MatchQueryUseCase matchQueryUseCase = mock(MatchQueryUseCase.class);

    @Override
    protected Object initController() {
        return new MatchController(matchQueryUseCase);
    }

    @DisplayName("GET /api/v1/matches/{matchId} — lol-server 와 같은 매치 응답")
    @Test
    void getMatch_storedMatch_returnsLolServerShape() throws Exception {
        // given
        given(matchQueryUseCase.getGame("KR_7123456789")).willReturn(game());

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}", "KR_7123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantData[0].timeCCingOthers").value(25))
                .andDo(document("match-get-by-id",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("matchId").description("조회할 매치 ID")
                        ),
                        responseFields(gameFields("data."))));
    }

    @DisplayName("저장되지 않은 매치는 404 NOT_FOUND_MATCH_ID (lol-server 와 같은 에러 본문)")
    @Test
    void getMatch_unknownMatch_returns404() throws Exception {
        // given
        given(matchQueryUseCase.getGame("ghost")).willThrow(new CoreException(ErrorType.NOT_FOUND_MATCH_ID));

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}", "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E404"))
                .andExpect(jsonPath("$.errorMessage.message").value("존재하지 않는 MatchId 입니다."));
    }

    @DisplayName("GET /api/v1/matches/{matchId}/timeline — lol-server 와 같은 타임라인 응답")
    @Test
    void getTimeline_storedTimeline_returnsLolServerShape() throws Exception {
        // given
        given(matchQueryUseCase.getTimeline("KR_123456789")).willReturn(new MatchTimelineReadModel(Map.of(
                1, new ParticipantTimeline(
                        List.of(new ItemSeq(1001, 1L, "ITEM_PURCHASED")),
                        List.of(new SkillSeq(1, 0L, "SKILL_LEVEL_UP"))))));

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}/timeline", "KR_123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants.1.itemSeq[0].itemId").value(1001))
                .andDo(document("match-get-timeline",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("matchId").description("조회할 매치 ID")
                        ),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),
                                fieldWithPath("data.participants").type(JsonFieldType.OBJECT).description("참가자별 타임라인 데이터 (key: participantId)"),
                                fieldWithPath("data.participants.*.itemSeq[]").type(JsonFieldType.ARRAY).description("아이템 구매 순서"),
                                fieldWithPath("data.participants.*.itemSeq[].itemId").type(JsonFieldType.NUMBER).description("아이템 ID"),
                                fieldWithPath("data.participants.*.itemSeq[].minute").type(JsonFieldType.NUMBER).description("구매 시간 (분)"),
                                fieldWithPath("data.participants.*.itemSeq[].type").type(JsonFieldType.STRING).description("이벤트 타입"),
                                fieldWithPath("data.participants.*.skillSeq[]").type(JsonFieldType.ARRAY).description("스킬 레벨업 순서"),
                                fieldWithPath("data.participants.*.skillSeq[].skillSlot").type(JsonFieldType.NUMBER).description("스킬 슬롯 (1-4)"),
                                fieldWithPath("data.participants.*.skillSeq[].minute").type(JsonFieldType.NUMBER).description("레벨업 시간 (분)"),
                                fieldWithPath("data.participants.*.skillSeq[].type").type(JsonFieldType.STRING).description("이벤트 타입")
                        )));
    }

    /** lol-server {@code fetchMatchesBySummoner} 의 테스트 데이터와 같다. */
    static GameReadModel game() {
        GameInfo gameInfo = new GameInfo("2", 1700000000000L, 1800L, 1700001800000L, "CLASSIC", 1700000000000L,
                "MATCHED_GAME", "14.1.1", 11, "KR", 420, "", "KR_7123456789", "IRON", "IV");

        ParticipantReadModel participant = ParticipantReadModel.builder()
                .profileIcon(4892)
                .riotIdGameName("TestSummoner").riotIdTagline("KR1")
                .puuid("test-puuid-1234").summonerLevel(350).summonerId("summoner-id-1234")
                .tier("DIAMOND").tierRank("I").absolutePoints(2850)
                .individualPosition("MIDDLE")
                .kills(10).deaths(2).assists(5)
                .champExperience(15000).champLevel(18)
                .championId(103).championName("Ahri")
                .consumablesPurchased(2).goldEarned(14500)
                .item(new Item(3157, 3020, 3089, 3135, 3116, 3165, 3340))
                .summoner1Id(4).summoner2Id(14)
                .itemsPurchased(15).participantId(1)
                .statValue(new StatValue(5002, 5008, 5005))
                .style(new Style(8100, 8112, 8126, 8138, 8135, 8300, 8304, 8347))
                .visionScore(32).totalMinionsKilled(185).neutralMinionsKilled(20)
                .totalDamageDealtToChampions(28000).totalDamageTaken(15000)
                .visionWardsBoughtInGame(3).wardsKilled(5).wardsPlaced(12)
                .doubleKills(2).tripleKills(1).quadraKills(0).pentaKills(0)
                .kda(7.5).teamDamagePercentage(28.5).goldPerMinute(480.0).killParticipation(62.5)
                .teamId(100).teamPosition("MIDDLE").win(true)
                .timePlayed(1800).timeCCingOthers(25)
                .lane("MIDDLE").role("SOLO")
                .placement(0)
                .playerAugment1(0).playerAugment2(0).playerAugment3(0).playerAugment4(0)
                .itemSeq(List.of(new ItemSeq(3157, 12L, "ITEM_PURCHASED")))
                .skillSeq(List.of(new SkillSeq(1, 1L, "SKILL_LEVEL_UP")))
                .build();

        TeamInfo blueTeam = new TeamInfo(100, true, 35, 2, 3, 9, 2,
                List.of(500, 3000, 8000, 15000, 25000), List.of(60000, 120000, 180000, 240000, 300000));
        TeamInfo redTeam = new TeamInfo(200, false, 20, 1, 1, 3, 0,
                List.of(450, 2800, 7500, 14000, 23000), List.of(60000, 120000, 180000, 240000, 300000));

        return new GameReadModel(gameInfo, List.of(participant), new Team(blueTeam, redTeam));
    }

    /**
     * lol-server {@code fetchMatchesBySummoner}({@code match-summoner-get-list}) 의 필드 정의.
     *
     * @param game 매치 한 건의 경로 (예: {@code data.}, {@code data.content[].})
     */
    static List<FieldDescriptor> gameFields(String game) {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),

                // GameInfoReadModel
                fieldWithPath(game + "gameInfoData.dataVersion").type(JsonFieldType.STRING).description("데이터 버전"),
                fieldWithPath(game + "gameInfoData.gameCreation").type(JsonFieldType.NUMBER).description("게임 생성 타임스탬프 (epoch ms)"),
                fieldWithPath(game + "gameInfoData.gameDuration").type(JsonFieldType.NUMBER).description("게임 지속 시간 (초)"),
                fieldWithPath(game + "gameInfoData.gameEndTimestamp").type(JsonFieldType.NUMBER).description("게임 종료 타임스탬프 (epoch ms)"),
                fieldWithPath(game + "gameInfoData.gameMode").type(JsonFieldType.STRING).description("게임 모드 (CLASSIC, ARAM 등)"),
                fieldWithPath(game + "gameInfoData.gameStartTimestamp").type(JsonFieldType.NUMBER).description("게임 시작 타임스탬프 (epoch ms)"),
                fieldWithPath(game + "gameInfoData.gameType").type(JsonFieldType.STRING).description("게임 타입"),
                fieldWithPath(game + "gameInfoData.gameVersion").type(JsonFieldType.STRING).description("게임 버전"),
                fieldWithPath(game + "gameInfoData.mapId").type(JsonFieldType.NUMBER).description("맵 ID (11: 소환사의 협곡, 12: 칼바람 나락)"),
                fieldWithPath(game + "gameInfoData.platformId").type(JsonFieldType.STRING).description("플랫폼 ID (KR, NA1 등)"),
                fieldWithPath(game + "gameInfoData.queueId").type(JsonFieldType.NUMBER).description("큐 ID (420: 솔로랭크, 430: 일반 등)"),
                fieldWithPath(game + "gameInfoData.tournamentCode").type(JsonFieldType.STRING).description("토너먼트 코드"),
                fieldWithPath(game + "gameInfoData.matchId").type(JsonFieldType.STRING).description("매치 ID"),
                fieldWithPath(game + "gameInfoData.averageTier").type(JsonFieldType.STRING).description("평균 티어 (IRON, BRONZE, SILVER, GOLD, PLATINUM, EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER)"),
                fieldWithPath(game + "gameInfoData.averageRank").type(JsonFieldType.STRING).description("평균 티어 등급 (I, II, III, IV / MASTER 이상은 null)"),

                // ParticipantReadModel - 유저 정보
                fieldWithPath(game + "participantData[].profileIcon").type(JsonFieldType.NUMBER).description("프로필 아이콘 ID"),
                fieldWithPath(game + "participantData[].riotIdGameName").type(JsonFieldType.STRING).description("Riot ID 게임 이름"),
                fieldWithPath(game + "participantData[].riotIdTagline").type(JsonFieldType.STRING).description("Riot ID 태그라인"),
                fieldWithPath(game + "participantData[].puuid").type(JsonFieldType.STRING).description("소환사 PUUID"),
                fieldWithPath(game + "participantData[].summonerLevel").type(JsonFieldType.NUMBER).description("소환사 레벨"),
                fieldWithPath(game + "participantData[].summonerId").type(JsonFieldType.STRING).description("소환사 ID"),
                fieldWithPath(game + "participantData[].tier").type(JsonFieldType.STRING).description("소환사 티어").optional(),
                fieldWithPath(game + "participantData[].tierRank").type(JsonFieldType.STRING).description("소환사 티어 등급 (I~IV)").optional(),
                fieldWithPath(game + "participantData[].absolutePoints").type(JsonFieldType.NUMBER).description("절대 포인트 (티어+등급+LP 수치화)").optional(),

                // ParticipantReadModel - 게임 정보
                fieldWithPath(game + "participantData[].individualPosition").type(JsonFieldType.STRING).description("개인 포지션 (TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY)"),
                fieldWithPath(game + "participantData[].kills").type(JsonFieldType.NUMBER).description("킬 수"),
                fieldWithPath(game + "participantData[].deaths").type(JsonFieldType.NUMBER).description("데스 수"),
                fieldWithPath(game + "participantData[].assists").type(JsonFieldType.NUMBER).description("어시스트 수"),
                fieldWithPath(game + "participantData[].champExperience").type(JsonFieldType.NUMBER).description("챔피언 경험치"),
                fieldWithPath(game + "participantData[].champLevel").type(JsonFieldType.NUMBER).description("챔피언 레벨"),
                fieldWithPath(game + "participantData[].championId").type(JsonFieldType.NUMBER).description("챔피언 ID"),
                fieldWithPath(game + "participantData[].championName").type(JsonFieldType.STRING).description("챔피언 이름"),
                fieldWithPath(game + "participantData[].consumablesPurchased").type(JsonFieldType.NUMBER).description("소모품 구매 횟수"),
                fieldWithPath(game + "participantData[].goldEarned").type(JsonFieldType.NUMBER).description("획득 골드"),
                fieldWithPath(game + "participantData[].summoner1Id").type(JsonFieldType.NUMBER).description("소환사 주문 1 ID"),
                fieldWithPath(game + "participantData[].summoner2Id").type(JsonFieldType.NUMBER).description("소환사 주문 2 ID"),
                fieldWithPath(game + "participantData[].itemsPurchased").type(JsonFieldType.NUMBER).description("아이템 구매 횟수"),
                fieldWithPath(game + "participantData[].participantId").type(JsonFieldType.NUMBER).description("참가자 ID"),
                fieldWithPath(game + "participantData[].visionScore").type(JsonFieldType.NUMBER).description("시야 점수"),
                fieldWithPath(game + "participantData[].totalMinionsKilled").type(JsonFieldType.NUMBER).description("총 미니언 처치 수"),
                fieldWithPath(game + "participantData[].neutralMinionsKilled").type(JsonFieldType.NUMBER).description("중립 몬스터 처치 수"),
                fieldWithPath(game + "participantData[].totalDamageDealtToChampions").type(JsonFieldType.NUMBER).description("챔피언에게 가한 총 피해량"),
                fieldWithPath(game + "participantData[].totalDamageTaken").type(JsonFieldType.NUMBER).description("받은 총 피해량"),
                fieldWithPath(game + "participantData[].visionWardsBoughtInGame").type(JsonFieldType.NUMBER).description("제어 와드 구매 횟수"),
                fieldWithPath(game + "participantData[].wardsKilled").type(JsonFieldType.NUMBER).description("와드 제거 횟수"),
                fieldWithPath(game + "participantData[].wardsPlaced").type(JsonFieldType.NUMBER).description("와드 설치 횟수"),
                fieldWithPath(game + "participantData[].doubleKills").type(JsonFieldType.NUMBER).description("더블킬 횟수"),
                fieldWithPath(game + "participantData[].tripleKills").type(JsonFieldType.NUMBER).description("트리플킬 횟수"),
                fieldWithPath(game + "participantData[].quadraKills").type(JsonFieldType.NUMBER).description("쿼드라킬 횟수"),
                fieldWithPath(game + "participantData[].pentaKills").type(JsonFieldType.NUMBER).description("펜타킬 횟수"),
                fieldWithPath(game + "participantData[].kda").type(JsonFieldType.NUMBER).description("KDA"),
                fieldWithPath(game + "participantData[].teamDamagePercentage").type(JsonFieldType.NUMBER).description("팀 피해 분담률 (%)"),
                fieldWithPath(game + "participantData[].goldPerMinute").type(JsonFieldType.NUMBER).description("분당 골드"),
                fieldWithPath(game + "participantData[].killParticipation").type(JsonFieldType.NUMBER).description("킬 관여율 (%)"),

                // ParticipantReadModel - 아이템
                fieldWithPath(game + "participantData[].item.item0").type(JsonFieldType.NUMBER).description("아이템 슬롯 0"),
                fieldWithPath(game + "participantData[].item.item1").type(JsonFieldType.NUMBER).description("아이템 슬롯 1"),
                fieldWithPath(game + "participantData[].item.item2").type(JsonFieldType.NUMBER).description("아이템 슬롯 2"),
                fieldWithPath(game + "participantData[].item.item3").type(JsonFieldType.NUMBER).description("아이템 슬롯 3"),
                fieldWithPath(game + "participantData[].item.item4").type(JsonFieldType.NUMBER).description("아이템 슬롯 4"),
                fieldWithPath(game + "participantData[].item.item5").type(JsonFieldType.NUMBER).description("아이템 슬롯 5"),
                fieldWithPath(game + "participantData[].item.item6").type(JsonFieldType.NUMBER).description("아이템 슬롯 6 (장신구)"),

                // ParticipantReadModel - 스탯
                fieldWithPath(game + "participantData[].statValue.defense").type(JsonFieldType.NUMBER).description("룬 방어 스탯"),
                fieldWithPath(game + "participantData[].statValue.flex").type(JsonFieldType.NUMBER).description("룬 유연 스탯"),
                fieldWithPath(game + "participantData[].statValue.offense").type(JsonFieldType.NUMBER).description("룬 공격 스탯"),

                // ParticipantReadModel - 룬 스타일
                fieldWithPath(game + "participantData[].style.primaryStyleId").type(JsonFieldType.NUMBER).description("주 룬 트리 ID"),
                fieldWithPath(game + "participantData[].style.primaryPerk0").type(JsonFieldType.NUMBER).description("주 룬 0"),
                fieldWithPath(game + "participantData[].style.primaryPerk1").type(JsonFieldType.NUMBER).description("주 룬 1"),
                fieldWithPath(game + "participantData[].style.primaryPerk2").type(JsonFieldType.NUMBER).description("주 룬 2"),
                fieldWithPath(game + "participantData[].style.primaryPerk3").type(JsonFieldType.NUMBER).description("주 룬 3"),
                fieldWithPath(game + "participantData[].style.subStyleId").type(JsonFieldType.NUMBER).description("부 룬 트리 ID"),
                fieldWithPath(game + "participantData[].style.subPerk0").type(JsonFieldType.NUMBER).description("부 룬 0"),
                fieldWithPath(game + "participantData[].style.subPerk1").type(JsonFieldType.NUMBER).description("부 룬 1"),

                // ParticipantReadModel - 팀 정보
                fieldWithPath(game + "participantData[].teamId").type(JsonFieldType.NUMBER).description("팀 ID (100: 블루, 200: 레드)"),
                fieldWithPath(game + "participantData[].teamPosition").type(JsonFieldType.STRING).description("팀 내 포지션"),
                fieldWithPath(game + "participantData[].win").type(JsonFieldType.BOOLEAN).description("승리 여부"),
                fieldWithPath(game + "participantData[].timePlayed").type(JsonFieldType.NUMBER).description("플레이 시간 (초)"),
                fieldWithPath(game + "participantData[].timeCCingOthers").type(JsonFieldType.NUMBER).description("CC기 적중 시간"),
                fieldWithPath(game + "participantData[].lane").type(JsonFieldType.STRING).description("라인"),
                fieldWithPath(game + "participantData[].role").type(JsonFieldType.STRING).description("역할"),

                // ParticipantReadModel - 아레나
                fieldWithPath(game + "participantData[].placement").type(JsonFieldType.NUMBER).description("아레나 배치 순위 (일반 게임: 0)"),
                fieldWithPath(game + "participantData[].playerAugment1").type(JsonFieldType.NUMBER).description("아레나 증강 1 (일반 게임: 0)"),
                fieldWithPath(game + "participantData[].playerAugment2").type(JsonFieldType.NUMBER).description("아레나 증강 2 (일반 게임: 0)"),
                fieldWithPath(game + "participantData[].playerAugment3").type(JsonFieldType.NUMBER).description("아레나 증강 3 (일반 게임: 0)"),
                fieldWithPath(game + "participantData[].playerAugment4").type(JsonFieldType.NUMBER).description("아레나 증강 4 (일반 게임: 0)"),

                // ParticipantReadModel - 타임라인
                fieldWithPath(game + "participantData[].itemSeq[].itemId").type(JsonFieldType.NUMBER).description("아이템 ID"),
                fieldWithPath(game + "participantData[].itemSeq[].minute").type(JsonFieldType.NUMBER).description("구매 시간 (분)"),
                fieldWithPath(game + "participantData[].itemSeq[].type").type(JsonFieldType.STRING).description("이벤트 타입 (ITEM_PURCHASED 등)"),
                fieldWithPath(game + "participantData[].skillSeq[].skillSlot").type(JsonFieldType.NUMBER).description("스킬 슬롯 (1-4)"),
                fieldWithPath(game + "participantData[].skillSeq[].minute").type(JsonFieldType.NUMBER).description("레벨업 시간 (분)"),
                fieldWithPath(game + "participantData[].skillSeq[].type").type(JsonFieldType.STRING).description("이벤트 타입 (SKILL_LEVEL_UP 등)")
        ));
        fields.addAll(teamFields(game + "teamInfoData.blueTeam.", "블루팀", "100"));
        fields.addAll(teamFields(game + "teamInfoData.redTeam.", "레드팀", "200"));
        return fields;
    }

    private static List<FieldDescriptor> teamFields(String team, String name, String teamId) {
        return List.of(
                fieldWithPath(team + "teamId").type(JsonFieldType.NUMBER).description(name + " ID (" + teamId + ")"),
                fieldWithPath(team + "win").type(JsonFieldType.BOOLEAN).description(name + " 승리 여부"),
                fieldWithPath(team + "championKills").type(JsonFieldType.NUMBER).description(name + " 총 킬 수"),
                fieldWithPath(team + "baronKills").type(JsonFieldType.NUMBER).description(name + " 바론 처치 수"),
                fieldWithPath(team + "dragonKills").type(JsonFieldType.NUMBER).description(name + " 드래곤 처치 수"),
                fieldWithPath(team + "towerKills").type(JsonFieldType.NUMBER).description(name + " 타워 파괴 수"),
                fieldWithPath(team + "inhibitorKills").type(JsonFieldType.NUMBER).description(name + " 억제기 파괴 수"),
                fieldWithPath(team + "goldTimeline[]").type(JsonFieldType.ARRAY).description(name + " 타임라인별 누적 골드"),
                fieldWithPath(team + "timestamps[]").type(JsonFieldType.ARRAY).description(name + " 골드 타임라인 타임스탬프 (ms)"));
    }
}
