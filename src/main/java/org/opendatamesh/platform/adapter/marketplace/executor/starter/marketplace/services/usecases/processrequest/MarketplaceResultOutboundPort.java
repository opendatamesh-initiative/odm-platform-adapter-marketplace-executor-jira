package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;

public interface MarketplaceResultOutboundPort {

    void sendResult(String requestIdentifier, MarketplaceResult result);
}
