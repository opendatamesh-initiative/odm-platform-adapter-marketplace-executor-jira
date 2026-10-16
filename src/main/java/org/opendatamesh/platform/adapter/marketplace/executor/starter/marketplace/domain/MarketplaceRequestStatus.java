package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

public enum MarketplaceRequestStatus {
    //The grant process has started but needs some additional work to be done
    PENDING,
    //The permissions have been correctly granted
    GRANTED,
    //The grand process has been denied
    DENIED,
    //The permissions have been correctly revoked
    REVOKED,
    //An error occurred during the grant process
    ERROR
} 