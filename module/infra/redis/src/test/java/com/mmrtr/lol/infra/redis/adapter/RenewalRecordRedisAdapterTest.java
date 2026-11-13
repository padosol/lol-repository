package com.mmrtr.lol.infra.redis.adapter;

import static com.mmrtr.lol.domain.summoner.domain.RenewalStep.ACCOUNT;
import static com.mmrtr.lol.domain.summoner.domain.RenewalStep.LEAGUE;
import static com.mmrtr.lol.domain.summoner.domain.RenewalStep.MATCH;
import static com.mmrtr.lol.domain.summoner.domain.RenewalStep.SUMMONER;
import static com.mmrtr.lol.domain.summoner.domain.RenewalTransition.FAILED;
import static com.mmrtr.lol.domain.summoner.domain.RenewalTransition.FAILED_LATE_COMPLETED;
import static com.mmrtr.lol.domain.summoner.domain.RenewalTransition.LATE_COMPLETED;
import static com.mmrtr.lol.domain.summoner.domain.RenewalTransition.NONE;
import static com.mmrtr.lol.domain.summoner.domain.RenewalTransition.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import java.util.EnumSet;
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
class RenewalRecordRedisAdapterTest {

    private static final String KEY = "summoner:renewal-v2:p1";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private RenewalRecordRedisAdapter adapter;

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
        adapter = new RenewalRecordRedisAdapter(redis);
    }

    @Test
    @DisplayName("open 은 5초 기한의 PROGRESS 레코드를 5분 TTL 로 쓰고 active 에 올린다")
    void open_writesProgressWithFiveSecondDeadline() {
        // when
        long accepted = adapter.open("p1", "r1", "KR");

        // then
        RenewalRecord r = adapter.find("p1").orElseThrow();
        assertThat(r.status()).isEqualTo(RenewalStatus.PROGRESS);
        assertThat(r.renewalId()).isEqualTo("r1");
        assertThat(r.platform()).isEqualTo("KR");
        assertThat(r.acceptedAt()).isEqualTo(accepted);
        assertThat(r.deadlineAt()).isEqualTo(accepted + 5000);
        assertThat(r.steps()).isZero();
        assertThat(adapter.findActive()).contains("p1");
        assertThat(redis.getExpire(KEY)).isBetween(290L, 300L);
    }

    @Test
    @DisplayName("open 은 이전 레코드를 덮어쓴다")
    void open_overwritesPreviousRecord() {
        // given
        adapter.open("p1", "r1", "KR");
        adapter.markSteps("p1", "r1", EnumSet.of(ACCOUNT));

        // when
        adapter.open("p1", "r2", "KR");

        // then
        RenewalRecord r = adapter.find("p1").orElseThrow();
        assertThat(r.renewalId()).isEqualTo("r2");
        assertThat(r.steps()).isZero();
    }

    @Test
    @DisplayName("기한 안에 모든 단계가 끝나면 SUCCEEDED 를 한 번 돌려준다")
    void markSteps_allStepsBeforeDeadline_succeedsOnce() {
        // given
        adapter.open("p1", "r1", "KR");

        // when & then
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(ACCOUNT, SUMMONER, LEAGUE))).isEqualTo(NONE);
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(MATCH))).isEqualTo(SUCCEEDED);
        assertThat(adapter.findActive()).doesNotContain("p1");
        assertThat(redis.getExpire(KEY)).isLessThanOrEqualTo(30L);
        assertThat(adapter.find("p1").orElseThrow().status()).isEqualTo(RenewalStatus.SUCCESS);
    }

    @Test
    @DisplayName("같은 단계가 두 번 와도 전이는 한 번")
    void markSteps_sameStepTwice_secondReturnsNone() {
        // given
        adapter.open("p1", "r1", "KR");
        adapter.markSteps("p1", "r1", EnumSet.of(ACCOUNT, SUMMONER, LEAGUE));

        // when & then
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(MATCH))).isEqualTo(SUCCEEDED);
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(MATCH))).isEqualTo(NONE);
    }

    @Test
    @DisplayName("다른 renewalId 의 쓰기는 무시한다")
    void markSteps_otherRenewalId_ignored() {
        // given
        adapter.open("p1", "r2", "KR");

        // when & then
        assertThat(adapter.markSteps("p1", "r1", EnumSet.allOf(com.mmrtr.lol.domain.summoner.domain.RenewalStep.class)))
                .isEqualTo(NONE);
        assertThat(adapter.fail("p1", "r1", "x")).isEqualTo(NONE);
        adapter.saveRiotRevisionDate("p1", "r1", 5L);
        RenewalRecord r = adapter.find("p1").orElseThrow();
        assertThat(r.steps()).isZero();
        assertThat(r.status()).isEqualTo(RenewalStatus.PROGRESS);
        assertThat(r.riotRevisionDate()).isNull();
    }

    @Test
    @DisplayName("레코드가 없으면 쓰기는 NONE 이고 아무것도 만들지 않는다")
    void writes_withoutRecord_noop() {
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(ACCOUNT))).isEqualTo(NONE);
        assertThat(adapter.fail("p1", "r1", "x")).isEqualTo(NONE);
        assertThat(adapter.failIfOverdue("p1", "r1")).isEqualTo(NONE);
        adapter.saveRiotRevisionDate("p1", "r1", 5L);
        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("기한 실패는 active 에 남아 늦은 완료를 한 번 돌려준다")
    void fail_deadline_keepsActiveForLateCompletion_thenLateCompletedOnce() {
        // given
        adapter.open("p1", "r1", "KR");
        redis.opsForHash().put(KEY, "deadlineAt", "1");

        // when & then
        assertThat(adapter.failIfOverdue("p1", "r1")).isEqualTo(FAILED);
        RenewalRecord r = adapter.find("p1").orElseThrow();
        assertThat(r.status()).isEqualTo(RenewalStatus.FAILED);
        assertThat(r.isDeadlineFailure()).isTrue();
        assertThat(adapter.findActive()).contains("p1");
        assertThat(redis.getExpire(KEY)).isBetween(290L, 300L);
        assertThat(adapter.failIfOverdue("p1", "r1")).isEqualTo(NONE);

        assertThat(adapter.markSteps("p1", "r1", EnumSet.allOf(com.mmrtr.lol.domain.summoner.domain.RenewalStep.class)))
                .isEqualTo(LATE_COMPLETED);
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(MATCH))).isEqualTo(NONE);
        assertThat(adapter.findActive()).doesNotContain("p1");
    }

    @Test
    @DisplayName("기한 뒤에 마지막 비트가 오면 FAILED(deadline) + 늦은 완료")
    void markSteps_lastBitAfterDeadline_failedLateCompleted() {
        // given
        adapter.open("p1", "r1", "KR");
        redis.opsForHash().put(KEY, "deadlineAt", "1");

        // when
        var result = adapter.markSteps("p1", "r1",
                EnumSet.allOf(com.mmrtr.lol.domain.summoner.domain.RenewalStep.class));

        // then
        assertThat(result).isEqualTo(FAILED_LATE_COMPLETED);
        RenewalRecord r = adapter.find("p1").orElseThrow();
        assertThat(r.status()).isEqualTo(RenewalStatus.FAILED);
        assertThat(r.reason()).isEqualTo("deadline");
        assertThat(adapter.findActive()).doesNotContain("p1");
        assertThat(redis.getExpire(KEY)).isBetween(290L, 300L);
        assertThat(adapter.markSteps("p1", "r1", EnumSet.of(MATCH))).isEqualTo(NONE);
    }

    @Test
    @DisplayName("일반 실패는 active 에서 빼고 TTL 을 30초로 줄인다")
    void fail_nonDeadline_removesActiveAndShortTtl() {
        // given
        adapter.open("p1", "r1", "KR");

        // when & then
        assertThat(adapter.fail("p1", "r1", "not_found")).isEqualTo(FAILED);
        assertThat(adapter.findActive()).doesNotContain("p1");
        assertThat(redis.getExpire(KEY)).isLessThanOrEqualTo(30L);
        assertThat(adapter.find("p1").orElseThrow().reason()).isEqualTo("not_found");
        assertThat(adapter.fail("p1", "r1", "other")).isEqualTo(NONE);
    }

    @Test
    @DisplayName("기한 전의 failIfOverdue 는 NONE")
    void failIfOverdue_beforeDeadline_none() {
        // given
        adapter.open("p1", "r1", "KR");

        // when & then
        assertThat(adapter.failIfOverdue("p1", "r1")).isEqualTo(NONE);
        assertThat(adapter.find("p1").orElseThrow().status()).isEqualTo(RenewalStatus.PROGRESS);
    }

    @Test
    @DisplayName("revisionDate 를 기록한다")
    void saveRiotRevisionDateWritesField() {
        // given
        adapter.open("p1", "r1", "KR");

        // when
        adapter.saveRiotRevisionDate("p1", "r1", 1234L);

        // then
        assertThat(adapter.find("p1").orElseThrow().riotRevisionDate()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("다른 renewalId 의 update 는 무시한다")
    void updateIgnoredForOtherRenewalId() {
        // given
        adapter.open("p1", "r2", "KR");

        // when
        adapter.saveRiotRevisionDate("p1", "r1", 9L);

        // then
        assertThat(adapter.find("p1").orElseThrow().riotRevisionDate()).isNull();
    }

    @Test
    @DisplayName("어떤 동작 뒤에도 레코드 말고 다른 키는 만들어지지 않는다")
    void noTargetsSetKeyIsCreated() {
        // given
        adapter.open("p1", "r1", "KR");
        adapter.markSteps("p1", "r1", EnumSet.allOf(com.mmrtr.lol.domain.summoner.domain.RenewalStep.class));
        adapter.open("p1", "r2", "KR");
        adapter.fail("p1", "r2", "x");

        // then
        assertThat(redis.hasKey(KEY + ":matches")).isFalse();
        assertThat(redis.keys("summoner:renewal-v2:*")).containsExactlyInAnyOrder(KEY);
    }

    @Test
    @DisplayName("deactivate 는 active 에서 뺀다")
    void deactivate_removesFromActive() {
        // given
        adapter.open("p1", "r1", "KR");

        // when
        adapter.deactivate("p1");

        // then
        assertThat(adapter.findActive()).isEmpty();
    }

    @Test
    @DisplayName("키는 lol-server 의 summoner:renewal:* 스캔 패턴 밖이다")
    void keys_areOutsideLolServerScanPattern() {
        // given
        adapter.open("p1", "r1", "KR");
        adapter.saveRiotRevisionDate("p1", "r1", 1L);

        // when & then
        assertThat(redis.keys("summoner:renewal:*")).isEmpty();
    }
}
