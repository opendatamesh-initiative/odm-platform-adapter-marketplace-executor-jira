package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.RegisteredAccessRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEventIgnoredReason;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

class CompleteTicket implements UseCase {

    private static final Logger log = Logger.getLogger(CompleteTicket.class);

    private final CompleteTicketCommand command;
    private final CompleteTicketPresenter presenter;
    private final CompleteTicketMarketplaceOutboundPort marketplacePort;
    private final CompleteTicketFilterOutboundPort filterPort;
    private final TicketOutcomeOutboundPort outcomePort;
    private final CompleteTicketMessageOutboundPort messagePort;
    private final TicketDetailsOutboundPort detailsPort;

    CompleteTicket(
            CompleteTicketCommand command,
            CompleteTicketPresenter presenter,
            CompleteTicketMarketplaceOutboundPort marketplacePort,
            CompleteTicketFilterOutboundPort filterPort,
            TicketOutcomeOutboundPort outcomePort,
            CompleteTicketMessageOutboundPort messagePort,
            TicketDetailsOutboundPort detailsPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.marketplacePort = marketplacePort;
        this.filterPort = filterPort;
        this.outcomePort = outcomePort;
        this.messagePort = messagePort;
        this.detailsPort = detailsPort;
    }

    @Override
    public void execute() {
        if (!command.event().statusTransition()) {
            ignore(TicketEventIgnoredReason.NOT_A_STATUS_TRANSITION);
            return;
        }
        if (correlationMissing()) {
            ignore(TicketEventIgnoredReason.MISSING_CORRELATION);
            return;
        }
        Optional<RegisteredAccessRequest> registered = marketplacePort.findAccessRequest(uuid());
        if (registered.isEmpty()) {
            ignore(TicketEventIgnoredReason.UNKNOWN_REQUEST);
            return;
        }
        completeRegisteredRequest(registered.get());
    }

    private void completeRegisteredRequest(RegisteredAccessRequest registered) {
        if (!filterPort.isHandledByThisExecutor(registered.operation(), registered.request())) {
            ignore(TicketEventIgnoredReason.NOT_HANDLED_BY_EXECUTOR);
        } else if (marketplacePort.hasFinalResult(uuid())) {
            ignore(TicketEventIgnoredReason.FINAL_RESULT_ALREADY_REPORTED);
        } else {
            Optional<TicketOutcome> outcome = outcomePort.evaluate(registered.operation(), command.event().fields());
            outcome.ifPresentOrElse(
                    terminal -> reportFinalResult(registered, terminal),
                    () -> ignore(TicketEventIgnoredReason.NON_TERMINAL_STATE));
        }
    }

    private void reportFinalResult(RegisteredAccessRequest registered, TicketOutcome outcome) {
        MarketplaceRequestStatus status = outcome.cause().statusFor(registered.operation());
        Map<String, String> fields = detailsPort.withLastComment(command.event().fields());
        String message = messagePort.finalMessage(registered.operation(), registered.request(), outcome, status, fields);
        marketplacePort.sendFinalResult(uuid(),
                new MarketplaceResult(status, message, registered.request().provider(), new Date()));
        log.infof("Jira issue event for request %s reported as %s", uuid(), status);
        presenter.presentReported(status);
    }

    private boolean correlationMissing() {
        return command.event().correlationId() == null || command.event().correlationId().isBlank();
    }

    private String uuid() {
        return command.event().correlationId();
    }

    private void ignore(TicketEventIgnoredReason reason) {
        log.infof("Jira issue event for request %s ignored: %s", command.event().correlationId(), reason);
        presenter.presentIgnored(reason);
    }
}
