package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;

import java.util.Map;
import java.util.Optional;

public interface TicketOutcomeOutboundPort {

    Optional<TicketOutcome> evaluate(MarketplaceOperation operation, Map<String, String> fields);
}
