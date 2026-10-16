package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.OdmApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.stream.Collectors;

public class ResponseExceptionMapper {

    private static final Logger log = Logger.getLogger(ResponseExceptionMapper.class);

    @ServerExceptionMapper
    public Response mapOdmException(OdmApiException e, UriInfo uriInfo) {
        if (e.getStatus() == Response.Status.INTERNAL_SERVER_ERROR) {
            log.error(e.getErrorName() + ":" + e.getMessage(), e);
        } else {
            log.info(e.getErrorName() + ":" + e.getMessage());
        }
        return buildResponse(e.getStatus().getStatusCode(), e.getErrorName(), e.getMessage(), uriInfo);
    }

    @ServerExceptionMapper
    public Response mapConstraintViolation(ConstraintViolationException e, UriInfo uriInfo) {
        String message = "Errors: " + e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(), "BadRequest", message, uriInfo);
    }

    @ServerExceptionMapper
    public Response mapRuntimeException(RuntimeException e, UriInfo uriInfo) {
        log.error("Unknown server error: ", e);
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "ServerError", "Unknown Internal Server Error", uriInfo);
    }

    private Response buildResponse(int status, String errorName, String message, UriInfo uriInfo) {
        ErrorResponse body = new ErrorResponse(status, errorName, message, uriInfo.getPath());
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
