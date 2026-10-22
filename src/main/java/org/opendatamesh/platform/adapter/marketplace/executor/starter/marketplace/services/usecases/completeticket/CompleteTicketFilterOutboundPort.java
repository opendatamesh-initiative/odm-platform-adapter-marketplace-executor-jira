package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

public interface CompleteTicketFilterOutboundPort {

    boolean isHandledByThisExecutor(MarketplaceOperation operation, MarketplaceRequest request);
}
