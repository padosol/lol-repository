package com.mmrtr.lol.controller.support;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개 포트({@code server.port}) / 내부 포트 분리 설정.
 *
 * @param internalPort                 {@code /internal/**} 만 받는 포트. NetworkPolicy 로 lol-server 파드만 허용한다.
 *                                     0 이하면 내부 포트를 열지 않는다.
 * @param transitionalPublicPrefixes   공개 포트에서 소유 prefix 외에 임시로 통과시키는 경로.
 *                                     Ingress 에는 없고 클러스터 내부에서만 호출된다.
 *                                     {@code /api/riot}(MP-161 에서 제거), {@code /api/admin}(MP-111 admin 포트로 이동),
 *                                     {@code /actuator}(프로브, management 포트 분리 시 제거)
 */
@ConfigurationProperties(prefix = "lol.web")
public record WebPortProperties(
        Integer internalPort,
        List<String> transitionalPublicPrefixes
) {

    public WebPortProperties {
        if (internalPort == null) {
            internalPort = 8081;
        }
        if (transitionalPublicPrefixes == null) {
            transitionalPublicPrefixes = List.of("/api/riot", "/api/admin", "/actuator");
        }
    }

    public boolean internalPortEnabled() {
        return internalPort > 0;
    }
}
