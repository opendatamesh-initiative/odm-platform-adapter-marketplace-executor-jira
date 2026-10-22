package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.JiraIssueService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

class JiraIssueOutboundPortImpl implements JiraIssueOutboundPort {

    private final JiraIssueService issueService;

    JiraIssueOutboundPortImpl(JiraIssueService issueService) {
        this.issueService = issueService;
    }

    @Override
    public IssueReference openIssue(MarketplaceOperation operation, MarketplaceRequest request) {
        return issueService.openIssue(operation, request);
    }
}
