package com.mmrtr.lol.infra.riot.dto.champion;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Riot champion-rotations 응답 역직렬화 회귀 테스트.
 *
 * <p>Riot 이 응답 스키마를 {@code freeChampionIds/freeChampionIdsForNewPlayers/maxNewPlayerLevel}
 * 에서 {@code sr/newplayer} 로 변경하면서 기존 필드명 매핑이 깨져 리스트가 null,
 * maxNewPlayerLevel 이 0 으로 비어 내려오던 문제를 고정한다.
 * {@code @JsonAlias} 로 구·신 포맷을 모두 수용하는지 검증한다.
 */
class ChampionInfoDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("신 포맷(sr/newplayer)을 freeChampionIds/freeChampionIdsForNewPlayers 로 매핑한다")
    void deserializesNewSchema() throws Exception {
        String json = """
                {"sr":[29,30,40,777,902],"newplayer":[17,18,875]}
                """;

        ChampionInfo info = objectMapper.readValue(json, ChampionInfo.class);

        assertThat(info.getFreeChampionIds())
                .containsExactly(29, 30, 40, 777, 902);
        assertThat(info.getFreeChampionIdsForNewPlayers())
                .containsExactly(17, 18, 875);
    }

    @Test
    @DisplayName("구 포맷(freeChampionIds/...)도 하위 호환으로 매핑한다")
    void deserializesLegacySchema() throws Exception {
        String json = """
                {"freeChampionIds":[1,2,3],"freeChampionIdsForNewPlayers":[4,5],"maxNewPlayerLevel":10}
                """;

        ChampionInfo info = objectMapper.readValue(json, ChampionInfo.class);

        assertThat(info.getFreeChampionIds())
                .containsExactly(1, 2, 3);
        assertThat(info.getFreeChampionIdsForNewPlayers())
                .containsExactly(4, 5);
        assertThat(info.getMaxNewPlayerLevel()).isEqualTo(10);
    }
}
