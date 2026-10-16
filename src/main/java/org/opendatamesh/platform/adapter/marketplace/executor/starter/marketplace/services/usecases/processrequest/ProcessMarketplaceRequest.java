package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.BadRequestException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase;

import org.jboss.logging.Logger;

import java.util.Date;

class ProcessMarketplaceRequest implements UseCase {

    private static final Logger log = Logger.getLogger(ProcessMarketplaceRequest.class);

    private final ProcessMarketplaceRequestCommand command;
    private final MarketplaceAccessOutboundPort accessPort;
    private final MarketplaceResultOutboundPort resultPort;
    private final AsyncExecutionOutboundPort asyncPort;

    ProcessMarketplaceRequest(
            ProcessMarketplaceRequestCommand command,
            MarketplaceAccessOutboundPort accessPort,
            MarketplaceResultOutboundPort resultPort,
            AsyncExecutionOutboundPort asyncPort
    ) {
        this.command = command;
        this.accessPort = accessPort;
        this.resultPort = resultPort;
        this.asyncPort = asyncPort;
    }

    @Override
    public void execute() {
        enforceInvariants();
        asyncPort.runAsync(this::processAndReport);
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

    private void processAndReport() {
        try {
            resultPort.sendResult(command.request().identifier(), applyOperation());
        } catch (Exception e) {
            resultPort.sendResult(command.request().identifier(), result(MarketplaceRequestStatus.ERROR, e.getMessage()));
            log.error(e.getMessage(), e);
        }
    }

    private MarketplaceResult applyOperation() {
        return switch (command.operation()) {
            case MARKETPLACE_SUBSCRIBE -> subscribe();
            case MARKETPLACE_UNSUBSCRIBE -> unsubscribe();
        };
    }

    private MarketplaceResult subscribe() {
        accessPort.grantAccess(command.request());
        return result(MarketplaceRequestStatus.GRANTED, "Subscription processed successfully");
    }

    private MarketplaceResult unsubscribe() {
        accessPort.revokeAccess(command.request());
        return result(MarketplaceRequestStatus.REVOKED, "Unsubscription processed successfully");
    }

    private MarketplaceResult result(MarketplaceRequestStatus status, String message) {
        return new MarketplaceResult(status, message, command.request().provider(), new Date());
    }
}
