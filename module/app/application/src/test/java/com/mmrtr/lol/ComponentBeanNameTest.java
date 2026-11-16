package com.mmrtr.lol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컴포넌트 스캔 빈 이름이 겹치지 않는지. 빈 이름은 클래스 단순 이름에서 나오므로 다른 모듈의 같은 이름 클래스
 * (예: 도메인과 rabbitmq 의 {@code *Service})가 부딪혀 기동이 {@code ConflictingBeanDefinitionException} 으로 실패한다.
 * CI 는 전체 컨텍스트를 띄우지 않으므로(DB·Redis·MQ 필요) 스캔만으로 막는다.
 */
class ComponentBeanNameTest {

    @DisplayName("스캔되는 컴포넌트의 빈 이름이 모두 다르다")
    @Test
    void componentBeanNamesAreUnique() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
        Map<String, List<String>> classesByBeanName = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.mmrtr.lol")) {
            String beanName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(definition, registry);
            classesByBeanName.computeIfAbsent(beanName, name -> new ArrayList<>()).add(definition.getBeanClassName());
        }

        assertThat(classesByBeanName).containsKey("summonerRenewalService");
        assertThat(classesByBeanName.entrySet())
                .filteredOn(entry -> entry.getValue().size() > 1)
                .as("같은 빈 이름을 쓰는 컴포넌트")
                .isEmpty();
    }
}
