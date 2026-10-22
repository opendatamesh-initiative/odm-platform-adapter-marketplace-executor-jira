package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;

import java.util.Map;

public interface CompleteTicketMessageOutboundPort {

    String finalMessage(MarketplaceOperation operation, MarketplaceRequest request, TicketOutcome outcome,
                        MarketplaceRequestStatus status, Map<String, String> fields);
}
