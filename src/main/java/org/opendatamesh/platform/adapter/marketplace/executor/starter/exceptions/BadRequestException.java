package org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions;

import jakarta.ws.rs.core.Response;

public class BadRequestException extends OdmApiException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getErrorName() {
        return getClass().getSimpleName();
    }

    @Override
    public Response.Status getStatus() {
        return Response.Status.BAD_REQUEST;
    }
}
