package com.mmrtr.lol.backfill.gcs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class GcsClientConfig {

    /**
     * BigQuery 와 같은 서비스 계정 키를 쓴다. 키 위치가 비면 ADC.
     */
    @Bean
    public Storage storage(BigQueryProperties props) throws IOException {
        if (!StringUtils.hasText(props.credentialsLocation())) {
            return StorageOptions.getDefaultInstance().getService();
        }
        Resource resource = new DefaultResourceLoader().getResource(props.credentialsLocation());
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        try (InputStream in = resource.getInputStream()) {
            builder.setCredentials(GoogleCredentials.fromStream(in));
        }
        if (StringUtils.hasText(props.projectId())) {
            builder.setProjectId(props.projectId());
        }
        return builder.build().getService();
    }
}
