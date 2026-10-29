package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ChampionStatsCacheEvictServiceTest {

    private final ChampionStatsCachePort port = mock(ChampionStatsCachePort.class);
    private final ChampionStatsCacheEvictService service = new ChampionStatsCacheEvictService(port);

    @DisplayName("포트에 위임하고 지운 수를 돌려준다")
    @Test
    void evictAll_delegatesToPort_returnsCount() {
        // given
        given(port.evictAll()).willReturn(7L);

        // when
        long evicted = service.evictAll();

        // then
        assertThat(evicted).isEqualTo(7L);
    }
}
