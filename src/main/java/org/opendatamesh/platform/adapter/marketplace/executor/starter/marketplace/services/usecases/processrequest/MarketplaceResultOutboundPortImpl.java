package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.core.MarketplaceServiceClient;

class MarketplaceResultOutboundPortImpl implements MarketplaceResultOutboundPort {

    private final MarketplaceServiceClient marketplaceClient;

    MarketplaceResultOutboundPortImpl(MarketplaceServiceClient marketplaceClient) {
        this.marketplaceClient = marketplaceClient;
    }

    @Override
    public void sendResult(String requestIdentifier, MarketplaceResult result) {
        marketplaceClient.sendResult(requestIdentifier, result);
    }
}
