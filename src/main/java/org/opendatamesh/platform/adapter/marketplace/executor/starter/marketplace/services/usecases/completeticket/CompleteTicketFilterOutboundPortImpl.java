package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.RequestFilterService;

class CompleteTicketFilterOutboundPortImpl implements CompleteTicketFilterOutboundPort {

    private final RequestFilterService filterService;

    CompleteTicketFilterOutboundPortImpl(RequestFilterService filterService) {
        this.filterService = filterService;
    }

    @Override
    public boolean isHandledByThisExecutor(MarketplaceOperation operation, MarketplaceRequest request) {
        return filterService.accepts(operation, request);
    }
}
