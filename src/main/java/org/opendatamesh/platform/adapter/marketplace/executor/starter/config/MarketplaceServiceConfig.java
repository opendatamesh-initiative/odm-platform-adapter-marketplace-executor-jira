package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "odm.product-plane.marketplace-service")
public interface MarketplaceServiceConfig {

    boolean active();

    String address();

    ResultDelivery resultDelivery();

    interface ResultDelivery {

        @WithDefault("5")
        int maxAttempts();

        @WithDefault("1s")
        Duration backoff();
    }
}
