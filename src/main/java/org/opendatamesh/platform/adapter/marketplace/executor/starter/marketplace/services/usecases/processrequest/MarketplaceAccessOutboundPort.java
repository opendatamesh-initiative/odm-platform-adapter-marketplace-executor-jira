package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

public interface MarketplaceAccessOutboundPort {

    void grantAccess(MarketplaceRequest request);

    void revokeAccess(MarketplaceRequest request);
}
