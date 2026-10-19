package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

public interface JiraIssueOutboundPort {

    IssueReference openIssue(MarketplaceOperation operation, MarketplaceRequest request);
}
