package org.opendatamesh.platform.adapter.marketplace.executor.starter.resources;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request for marketplace operations")
public class MarketplaceRequestRes {
    @Schema(description = "Version of the resource", required = true)
    @NotBlank(message = "Version is required")
    private String v;

    @Schema(description = "Operation type (MARKETPLACE_SUBSCRIBE or MARKETPLACE_UNSUBSCRIBE)", required = true)
    @NotBlank(message = "Operation is required")
    private String operation;

    @Schema(description = "Request information", required = true)
    @NotNull(message = "Request information is required")
    private RequestRes request;

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public RequestRes getRequest() {
        return request;
    }

    public void setRequest(RequestRes request) {
        this.request = request;
    }
} 