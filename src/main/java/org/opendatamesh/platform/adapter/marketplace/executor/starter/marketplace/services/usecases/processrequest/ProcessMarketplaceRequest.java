package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.BadRequestException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.RequestNotHandledException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase;
import org.jboss.logging.Logger;

import java.util.Date;

class ProcessMarketplaceRequest implements UseCase {

    private static final Logger log = Logger.getLogger(ProcessMarketplaceRequest.class);

    private final ProcessMarketplaceRequestCommand command;
    private final RequestFilterOutboundPort filterPort;
    private final JiraIssueOutboundPort issuePort;
    private final ResultMessageOutboundPort messagePort;
    private final MarketplaceResultOutboundPort resultPort;
    private final AsyncExecutionOutboundPort asyncPort;

    ProcessMarketplaceRequest(
            ProcessMarketplaceRequestCommand command,
            RequestFilterOutboundPort filterPort,
            JiraIssueOutboundPort issuePort,
            ResultMessageOutboundPort messagePort,
            MarketplaceResultOutboundPort resultPort,
            AsyncExecutionOutboundPort asyncPort
    ) {
        this.command = command;
        this.filterPort = filterPort;
        this.issuePort = issuePort;
        this.messagePort = messagePort;
        this.resultPort = resultPort;
        this.asyncPort = asyncPort;
    }

    @Override
    public void execute() {
        enforceInvariants();
        declineWhenNotHandled();
        asyncPort.runAsync(this::openIssueAndReport);
    }

    private void enforceInvariants() {
        if (command.request() == null) {
            throw new BadRequestException("Request details are required");
        }
        if (command.operation() == null) {
            throw new BadRequestException("Operation type is required");
        }
        if (startsAfterEnd(command.request())) {
            throw new BadRequestException("Start date must be before end date");
        }
    }

    private boolean startsAfterEnd(MarketplaceRequest request) {
        return request.startDate() != null && request.endDate() != null && request.startDate().after(request.endDate());
    }

    private void declineWhenNotHandled() {
        if (!filterPort.isHandledByThisExecutor(command.operation(), command.request())) {
            String reason = filterPort.mismatchReason(command.operation(), command.request());
            throw new RequestNotHandledException(
                    "Request " + command.request().identifier() + " is not handled by this Jira executor: " + reason);
        }
    }

    private void openIssueAndReport() {
        try {
            IssueReference issue = issuePort.openIssue(command.operation(), command.request());
            reportPending(issue);
        } catch (RuntimeException e) {
            reportTechnicalError(e);
        }
    }

    private void reportPending(IssueReference issue) {
        try {
            String message = messagePort.pendingMessage(command.operation(), command.request(), issue);
            resultPort.sendResult(command.request().identifier(), result(MarketplaceRequestStatus.PENDING, message));
        } catch (RuntimeException e) {
            log.warnf(e, "PENDING result for request %s not delivered; Jira issue %s is open",
                    command.request().identifier(), issue.key());
        }
    }

    private void reportTechnicalError(RuntimeException cause) {
        log.errorf("Unable to open Jira issue for request %s: %s", command.request().identifier(), cause.getMessage());
        try {
            String message = messagePort.technicalErrorMessage(command.operation(), command.request(), cause.getMessage());
            resultPort.sendResult(command.request().identifier(), result(MarketplaceRequestStatus.ERROR, message));
        } catch (RuntimeException e) {
            log.errorf(e, "ERROR result for request %s not delivered", command.request().identifier());
        }
    }

    private MarketplaceResult result(MarketplaceRequestStatus status, String message) {
        return new MarketplaceResult(status, message, command.request().provider(), new Date());
    }
}
