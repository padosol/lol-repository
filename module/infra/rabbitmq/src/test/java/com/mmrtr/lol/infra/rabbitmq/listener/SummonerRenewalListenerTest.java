package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRenewalService;
import com.mmrtr.lol.infra.redis.service.RedisLockHandler;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SummonerRenewalListenerTest {

    @Test
    @DisplayName("전적 갱신은 BACKGROUND 스코프에서 호출된다")
    void renewal_runsUnderBackground() {
        List<RiotCallPriority> seen = new ArrayList<>();
        RedisLockHandler lock = mock(RedisLockHandler.class);
        when(lock.acquireLock(anyString(), any())).thenReturn(true);
        SummonerRenewalService service = mock(SummonerRenewalService.class);
        doAnswer(inv -> {
            seen.add(RiotCallScope.current());
            return null;
        }).when(service).renewSummoner(anyString(), any(Platform.class));
        SummonerRenewalListener listener = new SummonerRenewalListener(lock, service);

        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        assertThat(seen).containsExactly(RiotCallPriority.BACKGROUND);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }
}
