package com.mmrtr.lol.domain.summoner.application.port;

import java.util.Optional;
import java.util.function.Supplier;

/** 저장되지 않은 소환사를 Riot 에서 찾아 저장하는 동안, 같은 소환사의 동시 조회가 Riot 을 중복 호출하지 않게 거는 분산 락. */
public interface SummonerLookupLockPort {

    /**
     * key 의 락을 잡고 action 을 실행한다. 대기 시간 안에 락을 잡지 못하면 action 을 실행하지 않고 empty.
     *
     * @param action null 을 돌려주지 않는다
     */
    <T> Optional<T> executeWithLock(String key, Supplier<T> action);
}
