package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.MarketplaceServiceConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services.programmatic.AsyncExecutorService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessMarketplaceRequestFactory {

    private final MarketplaceServiceConfig marketplaceServiceConfig;
    private final AsyncExecutorService asyncExecutorService;
    private final RestUtils restUtils;

    public ProcessMarketplaceRequestFactory(
            MarketplaceServiceConfig marketplaceServiceConfig,
            AsyncExecutorService asyncExecutorService,
            RestUtils restUtils
    ) {
        this.marketplaceServiceConfig = marketplaceServiceConfig;
        this.asyncExecutorService = asyncExecutorService;
        this.restUtils = restUtils;
    }

    public UseCase buildProcessMarketplaceRequest(ProcessMarketplaceRequestCommand command) {
        return new ProcessMarketplaceRequest(
                command,
                new MarketplaceAccessOutboundPortImpl(),
                new MarketplaceResultOutboundPortImpl(restUtils, marketplaceServiceConfig.address()),
                new AsyncExecutionOutboundPortImpl(asyncExecutorService)
        );
    }
}
