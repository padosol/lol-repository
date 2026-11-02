package com.mmrtr.lol.config;

import java.util.ArrayList;
import java.util.List;
import com.mmrtr.lol.infra.riot.config.RiotMockGuard;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

/**
 * lab 프로파일이 실험실 격리(DB lol_lab, Redis DB 1, vhost lab, 키 lab-key, 가짜 서버 주소) 위에서만 돈다는 걸 기동 때 강제한다.
 * 루트 .env 의 환경변수(SPRING_DATASOURCE_URL 등)가 lab 설정을 덮으면 앱이 조용히 평소 local DB 로 돌 수 있어서다.
 *
 * <p>BeanFactoryPostProcessor 로 만든 이유: BFPP 는 일반 싱글톤보다 먼저 생성된다. 그래서 DataSource·Flyway(어느 DB든 마이그레이션해 버린다)·
 * Redisson·Rabbit 리스너가 만들어지기 전에 실패해, 격리가 깨진 실행이 평소 인프라를 건드리지 못한다.
 * 생성자 주입은 BFPP 단계에서 동작하지 않으므로 {@link LabIsolationConfig} 가 static 빈으로 등록한다.
 * 메시지에는 속성 이름과 기대값만 쓰고 실제 값은 쓰지 않는다.
 */
public class LabIsolationGuard implements BeanFactoryPostProcessor {

    public LabIsolationGuard(Environment env) {
        List<String> violations = new ArrayList<>();
        String url = env.getProperty("spring.datasource.url", "");
        if (!(url.contains("/lol_lab?") || url.endsWith("/lol_lab"))) {
            violations.add("spring.datasource.url (DB 이름 lol_lab)");
        }
        expect(env, "spring.data.redis.database", "1", violations);
        expect(env, "spring.rabbitmq.virtual-host", "lab", violations);
        expect(env, "riot.api-key", "lab-key", violations);
        String mock = env.getProperty("riot.mock.base-url");
        if (mock == null || mock.isBlank()) {
            violations.add("riot.mock.base-url (비어 있지 않은 가짜 서버 주소)");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("lab 격리가 적용되지 않았습니다. 다음 설정을 확인하세요(환경변수가 yml 을 덮었을 수 있습니다): "
                    + String.join(", ", violations));
        }
        // dev·prod 와 lab 을 같이 켜면 위 5개 값은 lab yml 이 덮어 통과한다. RiotApiConfig 까지 기다리면 DataSource·Redisson 이 먼저 연결되므로 여기서 막는다.
        RiotMockGuard.assertAllowed(mock, env.getActiveProfiles());
    }

    private static void expect(Environment env, String name, String expected, List<String> violations) {
        String actual = env.getProperty(name);
        if (actual == null || !expected.equals(actual.trim())) {
            violations.add(name + " (기대값 " + expected + ")");
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 검사는 생성자에서 끝난다. BFPP 인 이유는 위 Javadoc 참고.
    }
}
