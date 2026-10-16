package org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions;

import jakarta.ws.rs.core.Response;

public class NotFoundException extends OdmApiException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getErrorName() {
        return getClass().getSimpleName();
    }

    @Override
    public Response.Status getStatus() {
        return Response.Status.NOT_FOUND;
    }
}
