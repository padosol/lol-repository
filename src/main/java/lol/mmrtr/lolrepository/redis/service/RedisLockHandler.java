package lol.mmrtr.lolrepository.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RedisLockHandler {
    private static final String UNLOCK_LUA =
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 분산 락을 획득하는 함수
     * * @param lockKey 락을 걸고자 하는 자원의 키 (예: "product:123:stock")
     * @param duration 락을 유지할 최대 시간
     * @return 획득 성공 시 락 해제에 사용할 고유 값(UUID), 실패 시 null
     */
    public boolean acquireLock(String puuid, Duration duration) {
        String lockKey = "summoner:lock:" + puuid;

        // 2. SET key value NX EX seconds 명령어 실행
        //    NX: 키가 존재하지 않을 때만 설정 (락 획득)
        //    EX seconds: 만료 시간 설정 (데드락 방지)
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(
                        lockKey,
                        puuid,
                        duration);

        if (Boolean.TRUE.equals(success)) {
            // 락 획득 성공
            return true;
        }

        // 락 획득 실패 (이미 다른 스레드가 락을 가지고 있음)
        return false;
    }

    /**
     * 획득한 락을 해제하는 함수 (반드시 획득 시 받은 lockValue를 사용해야 함)
     * * @param lockKey 락이 걸린 자원의 키
     * @param puuid 락 획득 시 발급받은 고유 값 (소유권 확인용)
     * @return 락 해제 성공 시 true, 실패(소유권 불일치 또는 락 만료) 시 false
     */
    public boolean releaseLock(String puuid) {
        String lockKey = "summoner:lock:" + puuid;

        // 1. Lua 스크립트 실행을 위한 RedisScript 객체 생성
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

        // 2. 스크립트 실행
        //    KEYS[1] = puuid
        //    ARGV[1] = puuid (소유권 확인 값)
        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                puuid
        );

        // 결과가 1이면 성공적으로 락을 해제했음을 의미 (Redis DEL 명령어의 반환값)
        return result != null && result == 1L;
    }

    public void deleteSummonerRenewal(String puuid) {
        stringRedisTemplate.delete(puuid);
    }
}
