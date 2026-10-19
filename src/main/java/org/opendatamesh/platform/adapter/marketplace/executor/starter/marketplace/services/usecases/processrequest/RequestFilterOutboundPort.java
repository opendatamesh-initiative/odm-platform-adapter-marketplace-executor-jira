package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

public interface RequestFilterOutboundPort {

    boolean isHandledByThisExecutor(MarketplaceOperation operation, MarketplaceRequest request);

    String mismatchReason(MarketplaceOperation operation, MarketplaceRequest request);
}
