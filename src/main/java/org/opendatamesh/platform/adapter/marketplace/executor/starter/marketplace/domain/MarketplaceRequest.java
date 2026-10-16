package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

import java.util.Date;
import java.util.Map;

public record MarketplaceRequest(
        String name,
        String identifier,
        Provider provider,
        Party consumer,
        Party requester,
        Map<String, Object> properties,
        Date startDate,
        Date endDate
) {
}
