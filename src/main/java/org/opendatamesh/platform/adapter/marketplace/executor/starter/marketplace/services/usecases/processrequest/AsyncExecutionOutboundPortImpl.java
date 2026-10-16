package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services.programmatic.AsyncExecutorService;

class AsyncExecutionOutboundPortImpl implements AsyncExecutionOutboundPort {

    private final AsyncExecutorService asyncExecutorService;

    AsyncExecutionOutboundPortImpl(AsyncExecutorService asyncExecutorService) {
        this.asyncExecutorService = asyncExecutorService;
    }

    @Override
    public void runAsync(Runnable work) {
        asyncExecutorService.execute(work);
    }
}
