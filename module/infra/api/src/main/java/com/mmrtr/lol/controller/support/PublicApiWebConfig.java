package com.mmrtr.lol.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(WebPortProperties.class)
public class PublicApiWebConfig {

    /** 내부 포트 connector 추가. 공개 포트는 {@code server.port} 그대로. */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> internalPortConnector(WebPortProperties properties) {
        return factory -> {
            if (!properties.internalPortEnabled()) {
                return;
            }
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(properties.internalPort());
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    @Bean
    public FilterRegistrationBean<PublicApiPathFilter> publicApiPathFilter(
            WebPortProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<PublicApiPathFilter> registration =
                new FilterRegistrationBean<>(new PublicApiPathFilter(properties, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
