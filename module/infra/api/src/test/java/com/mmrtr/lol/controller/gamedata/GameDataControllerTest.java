package com.mmrtr.lol.controller.gamedata;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.champion.application.usecase.ChampionRotationQueryUseCase;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.domain.gamedata.application.usecase.GameDataQueryUseCase;
import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteSummaryReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.AramChanges;
import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.ChangeEntry;
import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.GameModeChanges;
import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.PatchNoteContent;
import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.StatChange;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lol-server gamedata 컨트롤러 테스트(Version·Season·PatchNote·QueueType·Champion)의 응답 필드 정의를 그대로 옮겨
 * 새 경로의 응답이 lol-server 와 같은지 검증한다. responseFields 는 문서화되지 않은 필드가 있거나 빠지면 실패한다.
 */
class GameDataControllerTest extends RestDocsSupport {

    private final GameDataQueryUseCase gameDataQueryUseCase = mock(GameDataQueryUseCase.class);
    private final ChampionRotationQueryUseCase championRotationQueryUseCase = mock(ChampionRotationQueryUseCase.class);

    @Override
    protected Object initController() {
        return new GameDataController(gameDataQueryUseCase, championRotationQueryUseCase);
    }

    @DisplayName("GET /api/v1/game-data/versions/latest")
    @Test
    void latestVersion() throws Exception {
        given(gameDataQueryUseCase.getLatestVersion()).willReturn(
                new VersionReadModel(3L, "16.16", "16.16.1", LocalDateTime.of(2026, 8, 13, 10, 0)));

        mockMvc.perform(get("/api/v1/game-data/versions/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionValue").value("16.16"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-08-13T10:00:00"))
                .andDo(document("game-data-version-latest",
                        preprocessResponse(prettyPrint()),
                        responseFields(success(versionFields("data.")))));
    }

    @DisplayName("GET /api/v1/game-data/versions")
    @Test
    void recentVersions() throws Exception {
        given(gameDataQueryUseCase.getRecentVersions()).willReturn(List.of(
                new VersionReadModel(3L, "16.16", "16.16.1", LocalDateTime.of(2026, 8, 13, 10, 0)),
                new VersionReadModel(2L, "16.15", "16.15.1", LocalDateTime.of(2026, 7, 30, 10, 0))));

        List<FieldDescriptor> fields = new ArrayList<>();
        fields.add(fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("최근 버전 목록 (최신순, 최대 2개)"));
        fields.addAll(versionFields("data[]."));

        mockMvc.perform(get("/api/v1/game-data/versions"))
                .andExpect(status().isOk())
                .andDo(document("game-data-version-list",
                        preprocessResponse(prettyPrint()),
                        responseFields(success(fields))));
    }

    @DisplayName("GET /api/v1/game-data/versions/{versionId}")
    @Test
    void versionById() throws Exception {
        given(gameDataQueryUseCase.getVersionById(3L)).willReturn(
                new VersionReadModel(3L, "16.16", "16.16.1", LocalDateTime.of(2026, 8, 13, 10, 0)));

        mockMvc.perform(get("/api/v1/game-data/versions/{versionId}", 3L))
                .andExpect(status().isOk())
                .andDo(document("game-data-version-by-id",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("versionId").description("조회할 버전 ID")),
                        responseFields(success(versionFields("data.")))));
    }

    @DisplayName("없는 버전은 lol-server 와 같이 data=null 200")
    @Test
    void versionByIdNotFound() throws Exception {
        given(gameDataQueryUseCase.getVersionById(999L)).willReturn(null);

        mockMvc.perform(get("/api/v1/game-data/versions/{versionId}", 999L))
                .andExpect(status().isOk())
                .andDo(document("game-data-version-by-id-not-found",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("versionId").description("조회할 버전 ID")),
                        responseFields(success(List.of(
                                fieldWithPath("data").type(JsonFieldType.NULL).description("해당 버전 없음"))))));
    }

    @DisplayName("GET /api/v1/game-data/seasons")
    @Test
    void seasons() throws Exception {
        given(gameDataQueryUseCase.getAllSeasons()).willReturn(List.of(
                new SeasonReadModel(15, "2025 Season 1", List.of("25.S1.3", "25.S1.2")),
                new SeasonReadModel(14, "2024 Season 3", List.of("14.24.1"))));

        mockMvc.perform(get("/api/v1/game-data/seasons"))
                .andExpect(status().isOk())
                .andDo(document("game-data-season-list",
                        preprocessResponse(prettyPrint()),
                        responseFields(success(List.of(
                                fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("시즌 목록 (최신순)"),
                                fieldWithPath("data[].seasonValue").type(JsonFieldType.NUMBER).description("시즌 번호"),
                                fieldWithPath("data[].seasonName").type(JsonFieldType.STRING).description("시즌 이름"),
                                fieldWithPath("data[].patchVersions[]").type(JsonFieldType.ARRAY)
                                        .description("시즌에 속한 패치 버전 (최신순)"))))));
    }

    @DisplayName("GET /api/v1/game-data/seasons/{seasonId}")
    @Test
    void seasonById() throws Exception {
        given(gameDataQueryUseCase.getSeasonById(15L)).willReturn(
                new SeasonReadModel(15, "2025 Season 1", List.of("25.S1.3", "25.S1.2")));

        mockMvc.perform(get("/api/v1/game-data/seasons/{seasonId}", 15L))
                .andExpect(status().isOk())
                .andDo(document("game-data-season-detail",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("seasonId").description("조회할 시즌 ID")),
                        responseFields(success(List.of(
                                fieldWithPath("data.seasonValue").type(JsonFieldType.NUMBER).description("시즌 번호"),
                                fieldWithPath("data.seasonName").type(JsonFieldType.STRING).description("시즌 이름"),
                                fieldWithPath("data.patchVersions[]").type(JsonFieldType.ARRAY)
                                        .description("시즌에 속한 패치 버전 (최신순)"))))));
    }

    @DisplayName("GET /api/v1/game-data/patch-notes")
    @Test
    void patchNotes() throws Exception {
        given(gameDataQueryUseCase.getAllPatchNotes()).willReturn(List.of(
                new PatchNoteSummaryReadModel("26.S1.1", "패치 26.1 노트",
                        "https://www.leagueoflegends.com/ko-kr/news/game-updates/patch-26-1-notes/", "2026-01-15"),
                new PatchNoteSummaryReadModel("26.S1.2", "패치 26.2 노트",
                        "https://www.leagueoflegends.com/ko-kr/news/game-updates/patch-26-2-notes/", "2026-01-01")));

        mockMvc.perform(get("/api/v1/game-data/patch-notes"))
                .andExpect(status().isOk())
                .andDo(document("game-data-patch-note-list",
                        preprocessResponse(prettyPrint()),
                        responseFields(success(List.of(
                                fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("패치노트 요약 목록"),
                                fieldWithPath("data[].versionId").type(JsonFieldType.STRING).description("패치노트 버전 ID"),
                                fieldWithPath("data[].title").type(JsonFieldType.STRING).description("패치노트 제목"),
                                fieldWithPath("data[].patchUrl").type(JsonFieldType.STRING).optional()
                                        .description("패치노트 원본 URL (없으면 null)"),
                                fieldWithPath("data[].createdAt").type(JsonFieldType.STRING)
                                        .description("패치노트 생성일 (yyyy-MM-dd)"))))));
    }

    @DisplayName("GET /api/v1/game-data/patch-notes/{versionId}")
    @Test
    void patchNote() throws Exception {
        given(gameDataQueryUseCase.getPatchNote("26.S1.1")).willReturn(samplePatchNote("26.S1.1"));

        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("data.versionId").type(JsonFieldType.STRING).description("패치노트 버전 ID"),
                fieldWithPath("data.title").type(JsonFieldType.STRING).description("패치노트 제목"),
                fieldWithPath("data.content").type(JsonFieldType.OBJECT).description("패치노트 내용"),
                fieldWithPath("data.patchUrl").type(JsonFieldType.STRING).optional()
                        .description("패치노트 원본 URL (없으면 null)"),
                fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("패치노트 생성일 (yyyy-MM-dd)"),
                fieldWithPath("data.content.version").type(JsonFieldType.STRING).description("패치 버전"),
                fieldWithPath("data.content.rift").type(JsonFieldType.OBJECT).description("소환사의 협곡 변경사항"),
                fieldWithPath("data.content.arena").type(JsonFieldType.OBJECT).description("아레나 변경사항"),
                fieldWithPath("data.content.aram").type(JsonFieldType.OBJECT).description("칼바람 나락 변경사항")));
        for (String group : List.of("rift.champions", "rift.items", "rift.systems",
                "arena.champions", "arena.items", "arena.systems",
                "aram.champions", "aram.augments", "aram.items")) {
            fields.addAll(changeEntryFields("data.content." + group));
        }

        mockMvc.perform(get("/api/v1/game-data/patch-notes/{versionId}", "26.S1.1"))
                .andExpect(status().isOk())
                .andDo(document("game-data-patch-note-detail",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("versionId").description("조회할 패치노트 버전 ID")),
                        responseFields(success(fields))));
    }

    @DisplayName("없는 패치노트는 404 E404")
    @Test
    void patchNoteNotFound() throws Exception {
        given(gameDataQueryUseCase.getPatchNote(anyString()))
                .willThrow(new CoreException(ErrorType.NOT_FOUND_PATCH_NOTE));

        mockMvc.perform(get("/api/v1/game-data/patch-notes/{versionId}", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E404"))
                .andExpect(jsonPath("$.errorMessage.message").value("존재하지 않는 패치노트 입니다."));
    }

    @DisplayName("GET /api/v1/game-data/queues?tab=true (옛 /api/v1/queue-tab)")
    @Test
    void queueTabs() throws Exception {
        given(gameDataQueryUseCase.getQueues(true)).willReturn(List.of(
                new QueueInfo(420L, "솔로 랭크", true),
                new QueueInfo(430L, "일반", true),
                new QueueInfo(450L, "칼바람 나락", true)));

        mockMvc.perform(get("/api/v1/game-data/queues").param("tab", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andDo(document("game-data-queue-list",
                        preprocessResponse(prettyPrint()),
                        queryParameters(parameterWithName("tab").optional()
                                .description("true 면 전적 탭으로 노출하는 큐만 (기본 false = 전체)")),
                        responseFields(success(List.of(
                                fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("큐 목록"),
                                fieldWithPath("data[].queueId").type(JsonFieldType.NUMBER).description("큐 ID"),
                                fieldWithPath("data[].queueName").type(JsonFieldType.STRING).description("큐 이름"))))));
    }

    @DisplayName("GET /api/v1/game-data/champion-rotations?platform=kr")
    @Test
    void championRotation() throws Exception {
        given(championRotationQueryUseCase.getChampionRotation("kr")).willReturn(new ChampionRotation(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                List.of(101, 102, 103, 104, 105, 106, 107, 108, 109, 110),
                10));

        mockMvc.perform(get("/api/v1/game-data/champion-rotations").param("platform", "kr"))
                .andExpect(status().isOk())
                .andDo(document("game-data-champion-rotation",
                        preprocessResponse(prettyPrint()),
                        queryParameters(parameterWithName("platform").description("플랫폼 (e.g., kr) — 필수")),
                        responseFields(success(List.of(
                                fieldWithPath("data.maxNewPlayerLevel").type(JsonFieldType.NUMBER)
                                        .description("신규 플레이어 최대 레벨"),
                                fieldWithPath("data.freeChampionIdsForNewPlayers[]").type(JsonFieldType.ARRAY)
                                        .description("신규 플레이어용 무료 챔피언 ID 목록"),
                                fieldWithPath("data.freeChampionIds[]").type(JsonFieldType.ARRAY)
                                        .description("이번 주 로테이션 챔피언 ID 목록"))))));
    }

    @DisplayName("platform 누락은 400 INVALID_INPUT")
    @Test
    void championRotationWithoutPlatform() throws Exception {
        mockMvc.perform(get("/api/v1/game-data/champion-rotations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
    }

    private static List<FieldDescriptor> success(List<FieldDescriptor> dataFields) {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS)"),
                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)")));
        fields.addAll(dataFields);
        return fields;
    }

    private static List<FieldDescriptor> versionFields(String prefix) {
        return List.of(
                fieldWithPath(prefix + "versionId").type(JsonFieldType.NUMBER).description("버전 ID"),
                fieldWithPath(prefix + "versionValue").type(JsonFieldType.STRING).description("패치 버전 (예: 16.16)"),
                fieldWithPath(prefix + "patchVersionData").type(JsonFieldType.STRING).optional()
                        .description("Data Dragon 정적 데이터 버전 (예: 16.16.1, 과거 패치는 null)"),
                fieldWithPath(prefix + "createdAt").type(JsonFieldType.STRING).description("생성 일시"));
    }

    private static List<FieldDescriptor> changeEntryFields(String path) {
        return List.of(
                fieldWithPath(path + "[]").type(JsonFieldType.ARRAY).description("변경 목록"),
                fieldWithPath(path + "[].targetName").type(JsonFieldType.STRING).description("대상 이름"),
                fieldWithPath(path + "[].type").type(JsonFieldType.STRING).description("변경 유형"),
                fieldWithPath(path + "[].direction").type(JsonFieldType.STRING).description("변경 방향"),
                fieldWithPath(path + "[].changes[]").type(JsonFieldType.ARRAY).description("상세 변경사항"),
                fieldWithPath(path + "[].changes[].statName").type(JsonFieldType.STRING).description("스탯 이름"),
                fieldWithPath(path + "[].changes[].before").type(JsonFieldType.STRING).description("변경 전 값"),
                fieldWithPath(path + "[].changes[].after").type(JsonFieldType.STRING).description("변경 후 값"));
    }

    private static PatchNoteReadModel samplePatchNote(String versionId) {
        GameModeChanges rift = new GameModeChanges(
                List.of(entry("카시오페아", "champion", "buff", "기본 이동 속도", "328", "335")),
                List.of(entry("무한의 대검", "item", "nerf", "공격력", "70", "65")),
                List.of(entry("바론 버프", "system", "adjust", "공격력 증가", "40", "35")));
        GameModeChanges arena = new GameModeChanges(
                List.of(entry("야스오", "champion", "nerf", "받는 피해", "-5%", "0%")),
                List.of(entry("죽음의 무도", "item", "buff", "체력", "300", "350")),
                List.of(entry("아레나 전투 시간", "system", "adjust", "최대 시간", "30초", "25초")));
        AramChanges aram = new AramChanges(
                List.of(entry("럭스", "champion", "nerf", "주는 피해", "+5%", "0%")),
                List.of(entry("속사", "augment", "buff", "공격 속도", "20%", "25%")),
                List.of(entry("눈덩이", "item", "adjust", "재사용 대기시간", "60초", "50초")));
        return new PatchNoteReadModel(
                versionId,
                "패치 26.1 노트",
                new PatchNoteContent("26.1", rift, arena, aram),
                "https://www.leagueoflegends.com/ko-kr/news/game-updates/patch-26-1-notes/",
                "2026-01-15");
    }

    private static ChangeEntry entry(String target, String type, String direction,
                                     String stat, String before, String after) {
        return new ChangeEntry(target, type, direction, List.of(new StatChange(stat, before, after)));
    }
}
