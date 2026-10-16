package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

/**
 * Extension point: implement the actual access management on the enterprise tool.
 */
class MarketplaceAccessOutboundPortImpl implements MarketplaceAccessOutboundPort {

    @Override
    public void grantAccess(MarketplaceRequest request) {
        // TODO: Implement actual subscription logic
    }

    @Override
    public void revokeAccess(MarketplaceRequest request) {
        // TODO: Implement actual unsubscription logic
    }
}
