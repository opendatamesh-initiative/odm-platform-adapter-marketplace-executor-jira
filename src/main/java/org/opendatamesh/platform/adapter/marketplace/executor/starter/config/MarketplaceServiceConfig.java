package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "odm.product-plane.marketplace-service")
public interface MarketplaceServiceConfig {

    boolean active();

    String address();
}
