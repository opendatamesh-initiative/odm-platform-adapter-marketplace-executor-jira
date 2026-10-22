package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;

public interface ResultMessageOutboundPort {

    String pendingMessage(MarketplaceOperation operation, MarketplaceRequest request, IssueReference issue);

    String technicalErrorMessage(MarketplaceOperation operation, MarketplaceRequest request, String error);
}
