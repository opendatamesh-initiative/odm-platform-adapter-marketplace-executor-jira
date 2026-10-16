package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

import java.util.Date;

public record MarketplaceResult(MarketplaceRequestStatus status, String message, Provider provider, Date createdAt) {
}
