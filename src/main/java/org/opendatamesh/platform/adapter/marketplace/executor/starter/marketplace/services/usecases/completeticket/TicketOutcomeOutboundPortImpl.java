package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.OutcomeRulesService;

import java.util.Map;
import java.util.Optional;

class TicketOutcomeOutboundPortImpl implements TicketOutcomeOutboundPort {

    private final OutcomeRulesService outcomeRulesService;

    TicketOutcomeOutboundPortImpl(OutcomeRulesService outcomeRulesService) {
        this.outcomeRulesService = outcomeRulesService;
    }

    @Override
    public Optional<TicketOutcome> evaluate(MarketplaceOperation operation, Map<String, String> fields) {
        return outcomeRulesService.evaluate(operation, fields);
    }
}
