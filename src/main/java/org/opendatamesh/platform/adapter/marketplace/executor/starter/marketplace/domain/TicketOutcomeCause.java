package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

public enum TicketOutcomeCause {
    FULFILLED,
    REFUSED,
    FAILED;

    public MarketplaceRequestStatus statusFor(MarketplaceOperation operation) {
        return switch (operation) {
            case MARKETPLACE_SUBSCRIBE -> switch (this) {
                case FULFILLED -> MarketplaceRequestStatus.GRANTED;
                case REFUSED -> MarketplaceRequestStatus.DENIED;
                case FAILED -> MarketplaceRequestStatus.ERROR;
            };
            case MARKETPLACE_UNSUBSCRIBE -> switch (this) {
                case FULFILLED -> MarketplaceRequestStatus.REVOKED;
                case REFUSED, FAILED -> MarketplaceRequestStatus.ERROR;
            };
        };
    }
}
