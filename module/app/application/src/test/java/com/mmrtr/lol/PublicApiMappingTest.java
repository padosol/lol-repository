package com.mmrtr.lol;

import com.mmrtr.lol.controller.support.PublicApi;
import com.mmrtr.lol.controller.support.PublicApiPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 매핑 규칙 강제 (서비스 분리 설계 4.4).
 * <ul>
 *   <li>{@link PublicApi} 컨트롤러는 소유 prefix({@link PublicApiPaths#OWNED_PREFIXES}) 아래에만 매핑한다</li>
 *   <li>그 외 컨트롤러는 Ingress 에 올라가지 않는 경로({@code /internal}, 레거시 {@code /api/riot}·{@code /api/admin})에만 매핑한다</li>
 * </ul>
 */
class PublicApiMappingTest {

    /** Ingress 비공개 레거시 경로. /api/riot 은 MP-161 에서 제거, /api/admin 은 MP-111 admin 포트로 이동. */
    private static final List<String> NON_PUBLIC_LEGACY_PREFIXES = List.of("/api/riot", "/api/admin");

    @DisplayName("@PublicApi 컨트롤러는 소유 prefix 밖에 매핑하지 않는다")
    @Test
    void publicControllersStayInsideOwnedPrefixes() {
        List<String> violations = new ArrayList<>();
        for (Mapping mapping : scanMappings()) {
            if (mapping.publicApi() && !PublicApiPaths.isOwned(mapping.path())) {
                violations.add(mapping.toString());
            }
        }
        assertThat(violations).as("소유 prefix 밖의 공개 매핑").isEmpty();
    }

    @DisplayName("@PublicApi 가 아닌 컨트롤러는 비공개 경로에만 매핑한다")
    @Test
    void nonPublicControllersStayOutsidePublicPaths() {
        List<String> violations = new ArrayList<>();
        for (Mapping mapping : scanMappings()) {
            if (mapping.publicApi()) {
                continue;
            }
            boolean nonPublicPath = PublicApiPaths.isInternal(mapping.path())
                    || NON_PUBLIC_LEGACY_PREFIXES.stream().anyMatch(p -> PublicApiPaths.matches(mapping.path(), p));
            if (!nonPublicPath) {
                violations.add(mapping.toString());
            }
        }
        assertThat(violations).as("@PublicApi 없이 공개 경로에 매핑 (소유 prefix 면 @PublicApi 필요)").isEmpty();
    }

    @DisplayName("스캔이 실제 컨트롤러를 찾는다")
    @Test
    void scanFindsControllers() {
        assertThat(scanMappings())
                .extracting(Mapping::path)
                .anyMatch(path -> path.startsWith("/internal/backfill"))
                .anyMatch(path -> path.startsWith("/api/riot"));
    }

    private List<Mapping> scanMappings() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Mapping> mappings = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.mmrtr.lol")) {
            Class<?> type = ClassUtils.resolveClassName(definition.getBeanClassName(), getClass().getClassLoader());
            boolean publicApi = AnnotatedElementUtils.hasAnnotation(type, PublicApi.class);
            List<String> classPaths = paths(type);
            for (Method method : type.getMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                for (String classPath : classPaths) {
                    for (String methodPath : paths(method)) {
                        mappings.add(new Mapping(type.getSimpleName(), classPath + methodPath, publicApi));
                    }
                }
            }
        }
        return mappings;
    }

    private List<String> paths(AnnotatedElement element) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
        if (mapping == null || mapping.path().length == 0) {
            return List.of("");
        }
        return List.of(mapping.path());
    }

    private record Mapping(String controller, String path, boolean publicApi) {
        @Override
        public String toString() {
            return controller + " " + path;
        }
    }
}
