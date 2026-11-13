package com.mmrtr.lol.infra.redis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RenewalMatchClaimServiceTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private RenewalMatchClaimService service;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
        service = new RenewalMatchClaimService(redis);
    }

    @Test
    @DisplayName("다른 갱신은 선점에 실패하고 같은 갱신의 재전달은 성공한다")
    void claim_secondRenewalFalse_sameRenewalTrue() {
        // when & then
        assertThat(service.claim("m1", "r1")).isTrue();
        assertThat(service.claim("m1", "r2")).isFalse();
        assertThat(service.claim("m1", "r1")).isTrue();
    }

    @Test
    @DisplayName("release 는 선점한 갱신만 지울 수 있다")
    void release_onlyOwnerDeletes() {
        // given
        service.claim("m1", "r1");

        // when
        service.release("m1", "r2");

        // then
        assertThat(service.claim("m1", "r2")).isFalse();
        service.release("m1", "r1");
        assertThat(service.claim("m1", "r2")).isTrue();
    }

    @Test
    @DisplayName("실패를 세 번 표시하면 대상에서 제외된다")
    void markFailed_threeTimes_excluded() {
        // given
        service.markFailed("m1");
        service.markFailed("m1");
        assertThat(service.findExcluded(List.of("m1"))).isEmpty();

        // when
        service.markFailed("m1");

        // then
        assertThat(service.findExcluded(List.of("m1", "m2"))).containsExactly("m1");
        assertThat(service.findFailedAt(List.of("m1", "m2"))).containsOnlyKeys("m1");
    }

    @Test
    @DisplayName("실패 시각은 현재 ms 이고 TTL 은 5분, 횟수 TTL 은 1일이다")
    void markFailed_timestampAndTtl() {
        // given
        long before = System.currentTimeMillis();

        // when
        service.markFailed("m1");

        // then
        assertThat(service.findFailedAt(List.of("m1")).get("m1"))
                .isBetween(before, System.currentTimeMillis());
        assertThat(redis.getExpire("renewal:match:failed:m1")).isBetween(290L, 300L);
        assertThat(redis.getExpire("renewal:match:failed-count:m1")).isBetween(86_390L, 86_400L);
    }

    @Test
    @DisplayName("TTL 없이 남은 실패 횟수가 있어도 markFailed 뒤에는 횟수·표시 모두 TTL 이 있다")
    void markFailed_leftoverCountWithoutTtl_getsTtl() {
        // given — 옛 코드가 INCR 와 EXPIRE 사이에서 죽어 남긴 TTL 없는 횟수
        redis.opsForValue().set("renewal:match:failed-count:m1", "1");
        assertThat(redis.getExpire("renewal:match:failed-count:m1")).isEqualTo(-1L);

        // when
        service.markFailed("m1");

        // then
        assertThat(redis.opsForValue().get("renewal:match:failed-count:m1")).isEqualTo("2");
        assertThat(redis.getExpire("renewal:match:failed-count:m1")).isPositive();
        assertThat(redis.getExpire("renewal:match:failed:m1")).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("선점 TTL 은 1분이다")
    void claim_ttlOneMinute() {
        // when
        service.claim("m1", "r1");

        // then
        assertThat(redis.getExpire("renewal:match:claim:m1")).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("다른 갱신이 쥔 매치만 돌려준다. 내가 쥔 매치와 아무도 쥐지 않은 매치는 빠진다")
    void findClaimedByOthers_skipsOwnAndFree() {
        // given
        service.claim("m1", "r2");
        service.claim("m2", "r1");

        // when & then
        assertThat(service.findClaimedByOthers(List.of("m1", "m2", "m3"), "r1")).containsExactly("m1");
    }

    @Test
    @DisplayName("빈 목록은 빈 결과를 돌려준다")
    void find_emptyInput() {
        // when & then
        assertThat(service.findFailedAt(List.of())).isEmpty();
        assertThat(service.findExcluded(List.of())).isEmpty();
        assertThat(service.findClaimedByOthers(List.of(), "r1")).isEmpty();
    }
}
