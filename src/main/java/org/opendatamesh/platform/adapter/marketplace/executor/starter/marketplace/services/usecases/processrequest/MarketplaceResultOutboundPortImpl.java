package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.MarketplaceResponseRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.jboss.logging.Logger;

import java.util.List;

class MarketplaceResultOutboundPortImpl implements MarketplaceResultOutboundPort {

    private static final Logger log = Logger.getLogger(MarketplaceResultOutboundPortImpl.class);
    private static final String RESULTS_PATH = "/api/v1/pp/marketplace/requests/%s/results";

    private final RestUtils restUtils;
    private final String marketplaceAddress;

    MarketplaceResultOutboundPortImpl(RestUtils restUtils, String marketplaceAddress) {
        this.restUtils = restUtils;
        this.marketplaceAddress = marketplaceAddress;
    }

    @Override
    public void sendResult(String requestIdentifier, MarketplaceResult result) {
        waitForMarketplaceToRegisterRequest();
        try {
            restUtils.genericPost(
                    marketplaceAddress + RESULTS_PATH.formatted(requestIdentifier),
                    List.of(new HttpHeader(HttpHeader.CONTENT_TYPE, "application/json")),
                    toResponseRes(result),
                    Void.class
            );
        } catch (RuntimeException e) {
            log.warn(e.getMessage(), e);
        }
    }

    private void waitForMarketplaceToRegisterRequest() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private MarketplaceResponseRes toResponseRes(MarketplaceResult result) {
        MarketplaceResponseRes response = new MarketplaceResponseRes();
        response.setStatus(result.status());
        response.setMessage(result.message());
        response.setProvider(MarketplaceResourceMapper.toProviderRes(result.provider()));
        response.setCreatedAt(result.createdAt());
        return response;
    }
}
