package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.OdmApiException;

public interface UseCasePresenter {

    default void presentException(OdmApiException exception) {
        throw exception;
    }

    default void presentException(Exception exception) {
        throw new InternalException(exception.getMessage(), exception);
    }
}
