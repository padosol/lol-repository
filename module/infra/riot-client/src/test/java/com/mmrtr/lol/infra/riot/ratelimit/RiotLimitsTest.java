package com.mmrtr.lol.infra.riot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotMethod;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class RiotLimitsTest {

    @Test
    @DisplayName("app 한도와 method 한도를 읽고, 한도가 없는 method 를 알려 준다")
    void parsesAppAndMethodLimits() {
        RiotAPIProperties.RateLimit config = new RiotAPIProperties.RateLimit();
        config.setAppLimits("500:10,30000:600");
        config.setMethodLimits(Map.of("match-v5.match", "2000:10"));

        RiotLimits limits = RiotLimits.from(config);

        assertThat(limits.app()).containsExactly(new RateLimitWindow(500, 10), new RateLimitWindow(30000, 600));
        assertThat(limits.windowsOf(RiotMethod.MATCH)).containsExactly(new RateLimitWindow(2000, 10));
        assertThat(limits.windowsOf(RiotMethod.MATCH_TIMELINE)).isEmpty();
        assertThat(limits.unconfigured()).contains(RiotMethod.MATCH_TIMELINE).doesNotContain(RiotMethod.MATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "500", "500:10,abc", "0:10"})
    @DisplayName("app-limits 가 비었거나 한 항목이라도 형식이 틀리면 IllegalArgumentException")
    void rejectsBadAppLimits(String value) {
        RiotAPIProperties.RateLimit config = new RiotAPIProperties.RateLimit();
        config.setAppLimits(value);

        assertThatThrownBy(() -> RiotLimits.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riot.rate-limit.app-limits");
    }

    @Test
    @DisplayName("method 한도의 형식이 틀리면 그 이름을 담아 IllegalArgumentException")
    void rejectsBadMethodLimit() {
        RiotAPIProperties.RateLimit config = new RiotAPIProperties.RateLimit();
        config.setMethodLimits(Map.of("match-v5.match", "2000"));

        assertThatThrownBy(() -> RiotLimits.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match-v5.match");
    }

    @Test
    @DisplayName("RiotMethod 에 없는 이름(오타)이면 IllegalArgumentException")
    void rejectsUnknownMethod() {
        RiotAPIProperties.RateLimit config = new RiotAPIProperties.RateLimit();
        config.setMethodLimits(Map.of("match-v5.mach", "2000:10"));

        assertThatThrownBy(() -> RiotLimits.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match-v5.mach");
    }

    @ParameterizedTest
    @ValueSource(strings = {"riot-prod.yml", "riot-dev.yml", "riot-local.yml"})
    @DisplayName("프로파일 yml 이 바인딩되고 검증을 통과한다")
    void profileYmlsBindAndValidate(String file) throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);
        RiotAPIProperties bound = new Binder(ConfigurationPropertySources.from(sources))
                .bind("riot", RiotAPIProperties.class)
                .get();

        RiotLimits limits = RiotLimits.from(bound.getRateLimit());

        assertThat(limits.app()).containsExactly(new RateLimitWindow(500, 10), new RateLimitWindow(30000, 600));
        assertThat(limits.windowsOf(RiotMethod.MATCH)).containsExactly(new RateLimitWindow(2000, 10));
        assertThat(limits.windowsOf(RiotMethod.SUMMONER_BY_PUUID)).containsExactly(new RateLimitWindow(2000, 60));
    }

    @Test
    @DisplayName("환경 변수 RIOT_RATELIMIT_APPLIMITS 가 app-limits 를 덮어쓴다")
    void envOverridesAppLimits() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("RIOT_RATELIMIT_APPLIMITS", "20:1,100:120")));

        RiotAPIProperties bound = new Binder(ConfigurationPropertySources.from(sources))
                .bind("riot", RiotAPIProperties.class)
                .get();

        assertThat(bound.getRateLimit().getAppLimits()).isEqualTo("20:1,100:120");
    }
}
