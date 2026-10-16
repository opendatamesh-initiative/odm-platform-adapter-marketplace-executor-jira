package org.opendatamesh.platform.adapter.marketplace.executor.starter.resources;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requester information for the request")
public class RequesterRes {
    @Schema(description = "Requester type (user)", required = true)
    @NotBlank(message = "Requester type is required")
    private String type;

    @Schema(description = "Requester identifier (email)", required = true)
    @NotBlank(message = "Requester identifier is required")
    private String identifier;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
} 