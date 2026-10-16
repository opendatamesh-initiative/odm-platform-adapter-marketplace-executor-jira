package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

public interface AsyncExecutionOutboundPort {

    void runAsync(Runnable work);
}
