package com.mmrtr.lol.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** lab 프로파일에서만 {@link LabIsolationGuard} 를 static 빈(BFPP)으로 등록한다. */
@Configuration
@Profile("lab")
public class LabIsolationConfig {

    @Bean
    public static BeanFactoryPostProcessor labIsolationGuard(Environment env) {
        return new LabIsolationGuard(env);
    }
}
