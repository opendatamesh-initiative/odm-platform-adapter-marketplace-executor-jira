package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.marketplace.MarketplaceServiceClient;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.RequestFilterService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.ResultMessageService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.JiraIssueService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services.programmatic.AsyncExecutorService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessMarketplaceRequestFactory {

    private final RequestFilterService requestFilterService;
    private final JiraIssueService jiraIssueService;
    private final ResultMessageService resultMessageService;
    private final MarketplaceServiceClient marketplaceServiceClient;
    private final AsyncExecutorService asyncExecutorService;

    public ProcessMarketplaceRequestFactory(
            RequestFilterService requestFilterService,
            JiraIssueService jiraIssueService,
            ResultMessageService resultMessageService,
            MarketplaceServiceClient marketplaceServiceClient,
            AsyncExecutorService asyncExecutorService
    ) {
        this.requestFilterService = requestFilterService;
        this.jiraIssueService = jiraIssueService;
        this.resultMessageService = resultMessageService;
        this.marketplaceServiceClient = marketplaceServiceClient;
        this.asyncExecutorService = asyncExecutorService;
    }

    public UseCase buildProcessMarketplaceRequest(ProcessMarketplaceRequestCommand command) {
        return new ProcessMarketplaceRequest(
                command,
                new RequestFilterOutboundPortImpl(requestFilterService),
                new JiraIssueOutboundPortImpl(jiraIssueService),
                new ResultMessageOutboundPortImpl(resultMessageService),
                new MarketplaceResultOutboundPortImpl(marketplaceServiceClient),
                new AsyncExecutionOutboundPortImpl(asyncExecutorService)
        );
    }
}
