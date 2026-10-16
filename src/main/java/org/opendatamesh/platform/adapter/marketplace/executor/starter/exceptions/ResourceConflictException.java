package org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions;

import jakarta.ws.rs.core.Response;

public class ResourceConflictException extends OdmApiException {

    private static final long serialVersionUID = 1L;

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String message, Throwable cause) {
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
