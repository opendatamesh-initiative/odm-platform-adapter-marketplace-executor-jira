package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.marketplace.MarketplaceServiceClient;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.JiraIssueService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.OutcomeRulesService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.RequestFilterService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.ResultMessageService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CompleteTicketFactory {

    private final MarketplaceServiceClient marketplaceServiceClient;
    private final RequestFilterService requestFilterService;
    private final OutcomeRulesService outcomeRulesService;
    private final ResultMessageService resultMessageService;
    private final JiraIssueService jiraIssueService;

    public CompleteTicketFactory(
            MarketplaceServiceClient marketplaceServiceClient,
            RequestFilterService requestFilterService,
            OutcomeRulesService outcomeRulesService,
            ResultMessageService resultMessageService,
            JiraIssueService jiraIssueService
    ) {
        this.marketplaceServiceClient = marketplaceServiceClient;
        this.requestFilterService = requestFilterService;
        this.outcomeRulesService = outcomeRulesService;
        this.resultMessageService = resultMessageService;
        this.jiraIssueService = jiraIssueService;
    }

    public UseCase buildCompleteTicket(CompleteTicketCommand command, CompleteTicketPresenter presenter) {
        return new CompleteTicket(
                command,
                presenter,
                new CompleteTicketMarketplaceOutboundPortImpl(marketplaceServiceClient),
                new CompleteTicketFilterOutboundPortImpl(requestFilterService),
                new TicketOutcomeOutboundPortImpl(outcomeRulesService),
                new CompleteTicketMessageOutboundPortImpl(resultMessageService),
                new TicketDetailsOutboundPortImpl(jiraIssueService)
        );
    }
}
