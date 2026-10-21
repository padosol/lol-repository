package com.mmrtr.lol.infra.bigquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param credentialsLocation 서비스 계정 키 위치 (예: {@code file:/etc/bigquery/bigquery.json}). 비우면 ADC
 */
@ConfigurationProperties(prefix = "bigquery")
public record BigQueryProperties(
        String projectId,
        String dataset,
        String credentialsLocation
) {
}
