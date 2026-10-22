package org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions;

import jakarta.ws.rs.core.Response;

public class RequestNotHandledException extends OdmApiException {

    private static final long serialVersionUID = 1L;

    public RequestNotHandledException(String message) {
        super(message);
    }

    public RequestNotHandledException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getErrorName() {
        return getClass().getSimpleName();
    }

    @Override
    public Response.Status getStatus() {
        return Response.Status.CONFLICT;
    }
}
