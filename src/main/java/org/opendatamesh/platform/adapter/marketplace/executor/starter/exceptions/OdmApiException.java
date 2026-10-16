package org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions;

import jakarta.ws.rs.core.Response;

public abstract class OdmApiException extends RuntimeException {

    private static final long serialVersionUID = 3876573329263306459L;

    public OdmApiException() {
        super();
    }

    public OdmApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public OdmApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public OdmApiException(String message) {
        super(message);
    }

    public OdmApiException(Throwable cause) {
        super(cause);
    }

    public abstract String getErrorName();

    public abstract Response.Status getStatus();
}
