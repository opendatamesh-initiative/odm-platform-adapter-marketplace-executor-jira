package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.RegisteredAccessRequest;

import java.util.Optional;

public interface CompleteTicketMarketplaceOutboundPort {

    Optional<RegisteredAccessRequest> findAccessRequest(String uuid);

    boolean hasFinalResult(String uuid);

    void sendFinalResult(String uuid, MarketplaceResult result);
}
