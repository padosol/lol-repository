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
        assertThat(service.findClaimedByOthers(List.of(), "r1")).isEmpty();
    }
}
