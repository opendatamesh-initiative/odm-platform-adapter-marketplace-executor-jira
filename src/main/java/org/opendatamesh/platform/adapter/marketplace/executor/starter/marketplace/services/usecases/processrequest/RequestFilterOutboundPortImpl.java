package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.RequestFilterService;

class RequestFilterOutboundPortImpl implements RequestFilterOutboundPort {

    private final RequestFilterService filterService;

    RequestFilterOutboundPortImpl(RequestFilterService filterService) {
        this.filterService = filterService;
    }

    @Override
    public boolean isHandledByThisExecutor(MarketplaceOperation operation, MarketplaceRequest request) {
        return filterService.accepts(operation, request);
    }

    @Override
    public String mismatchReason(MarketplaceOperation operation, MarketplaceRequest request) {
        return filterService.describeMismatch(operation, request);
    }
}
