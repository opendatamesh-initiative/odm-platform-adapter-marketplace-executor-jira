package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.ResultMessageService;

class ResultMessageOutboundPortImpl implements ResultMessageOutboundPort {

    private final ResultMessageService messageService;

    ResultMessageOutboundPortImpl(ResultMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public String pendingMessage(MarketplaceOperation operation, MarketplaceRequest request, IssueReference issue) {
        return messageService.pending(operation, request, issue);
    }

    @Override
    public String technicalErrorMessage(MarketplaceOperation operation, MarketplaceRequest request, String error) {
        return messageService.technicalError(operation, request, error);
    }
}
