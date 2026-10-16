package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

@Provider
@ApplicationScoped
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "CORRELATION_ID";
    public static final String MDC_KEY = "correlation_id";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        MDC.remove(MDC_KEY);
        String correlationId = requestContext.getHeaderString(HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        MDC.remove(MDC_KEY);
    }
}
