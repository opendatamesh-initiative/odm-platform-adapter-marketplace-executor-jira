package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core.ResultMessageService;

import java.util.Map;

class CompleteTicketMessageOutboundPortImpl implements CompleteTicketMessageOutboundPort {

    private final ResultMessageService messageService;

    CompleteTicketMessageOutboundPortImpl(ResultMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public String finalMessage(MarketplaceOperation operation, MarketplaceRequest request, TicketOutcome outcome,
                               MarketplaceRequestStatus status, Map<String, String> fields) {
        return messageService.finalOutcome(operation, request, outcome, status, fields);
    }
}
