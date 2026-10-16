package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtilsFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.net.http.HttpClient;
import java.time.Duration;

public class RestUtilsProducer {

    @Produces
    @ApplicationScoped
    RestUtils restUtils(ObjectMapper objectMapper) {
        return RestUtilsFactory.getRestUtils(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build(),
                objectMapper
        );
    }
}
