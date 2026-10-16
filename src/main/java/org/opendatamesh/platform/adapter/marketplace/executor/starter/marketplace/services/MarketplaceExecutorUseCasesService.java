package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.BadRequestException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest.ProcessMarketplaceRequestCommand;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest.ProcessMarketplaceRequestFactory;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.ConsumerRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.MarketplaceRequestRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.ProviderRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.RequestRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.RequesterRes;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MarketplaceExecutorUseCasesService {

    private final ProcessMarketplaceRequestFactory processMarketplaceRequestFactory;

    public MarketplaceExecutorUseCasesService(ProcessMarketplaceRequestFactory processMarketplaceRequestFactory) {
        this.processMarketplaceRequestFactory = processMarketplaceRequestFactory;
    }

    public void processRequest(MarketplaceRequestRes requestRes) {
        ProcessMarketplaceRequestCommand command = toCommand(requestRes);
        processMarketplaceRequestFactory.buildProcessMarketplaceRequest(command).execute();
    }

    private ProcessMarketplaceRequestCommand toCommand(MarketplaceRequestRes requestRes) {
        return new ProcessMarketplaceRequestCommand(toOperation(requestRes.getOperation()), toRequest(requestRes.getRequest()));
    }

    private MarketplaceOperation toOperation(String operation) {
        try {
            return MarketplaceOperation.valueOf(operation);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid operation type. Must be either MARKETPLACE_SUBSCRIBE or MARKETPLACE_UNSUBSCRIBE");
        }
    }

    private MarketplaceRequest toRequest(RequestRes res) {
        if (res == null) {
            return null;
        }
        return new MarketplaceRequest(
                res.getName(),
                res.getIdentifier(),
                toProvider(res.getProvider()),
                toParty(res.getConsumer()),
                toParty(res.getRequester()),
                res.getProperties(),
                res.getStartDate(),
                res.getEndDate()
        );
    }

    private Provider toProvider(ProviderRes res) {
        return res == null ? null : new Provider(res.getDataProductFqn(), res.getDataProductPortsFqn());
    }

    private Party toParty(ConsumerRes res) {
        return res == null ? null : new Party(res.getType(), res.getIdentifier());
    }

    private Party toParty(RequesterRes res) {
        return res == null ? null : new Party(res.getType(), res.getIdentifier());
    }
}
