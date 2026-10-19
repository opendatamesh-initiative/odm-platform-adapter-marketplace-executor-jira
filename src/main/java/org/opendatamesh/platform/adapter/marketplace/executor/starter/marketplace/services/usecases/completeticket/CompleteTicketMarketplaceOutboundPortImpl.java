package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.RegisteredAccessRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.marketplace.MarketplaceServiceClient;

import java.util.Optional;

class CompleteTicketMarketplaceOutboundPortImpl implements CompleteTicketMarketplaceOutboundPort {

    private final MarketplaceServiceClient marketplaceClient;

    CompleteTicketMarketplaceOutboundPortImpl(MarketplaceServiceClient marketplaceClient) {
        this.marketplaceClient = marketplaceClient;
    }

    @Override
    public Optional<RegisteredAccessRequest> findAccessRequest(String uuid) {
        return marketplaceClient.findAccessRequest(uuid);
    }

    @Override
    public boolean hasFinalResult(String uuid) {
        return marketplaceClient.hasFinalResult(uuid);
    }

    @Override
    public void sendFinalResult(String uuid, MarketplaceResult result) {
        marketplaceClient.sendResult(uuid, result);
    }
}
