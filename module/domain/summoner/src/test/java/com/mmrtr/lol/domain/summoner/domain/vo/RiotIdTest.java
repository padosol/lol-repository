package com.mmrtr.lol.domain.summoner.domain.vo;

import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotIdTest {

    @DisplayName("마지막 - 로 이름과 태그를 나누고, search_name 은 공백을 빼고 소문자로 만든다")
    @Test
    void parse_nameWithHyphen_splitsAtLastHyphen() {
        // when
        RiotId riotId = RiotId.parse("Hide-On Bush-KR1");

        // then
        assertThat(riotId).isEqualTo(new RiotId("Hide-On Bush", "KR1"));
        assertThat(riotId.searchName()).isEqualTo("hide-onbush#kr1");
    }

    @DisplayName("- 가 없거나 태그가 비면 태그 없음, 접두사는 이름#")
    @Test
    void parse_withoutTagLine_hasNoTagLine() {
        // when & then
        assertThat(RiotId.parse("hide on bush").hasTagLine()).isFalse();
        assertThat(RiotId.parse("hide on bush-").hasTagLine()).isFalse();
        assertThat(RiotId.parse("hide on bush").searchNamePrefix()).isEqualTo("hideonbush#");
    }

    @DisplayName("이름이 비면 INVALID_INPUT 400 (lol-server 는 500)")
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "-KR1", " -KR1"})
    void parse_blankGameName_throwsInvalidInput(String value) {
        // when & then
        assertThatThrownBy(() -> RiotId.parse(value))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT));
    }
}
