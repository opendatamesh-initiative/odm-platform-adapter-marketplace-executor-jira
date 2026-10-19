package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.MarketplaceExecutorUseCasesService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.MarketplaceRequestRes;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/up/marketplace-executor")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Marketplace Executor", description = "API for handling marketplace subscription and unsubscription requests")
public class MarketplaceExecutorUseCaseResource {

    private final MarketplaceExecutorUseCasesService useCasesService;

    public MarketplaceExecutorUseCaseResource(MarketplaceExecutorUseCasesService useCasesService) {
        this.useCasesService = useCasesService;
    }

    @POST
    @Path("/requests")
    @Operation(summary = "Process a marketplace request", description = "Handles subscription and unsubscription requests for data products asynchronously")
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Request accepted for processing"),
            @APIResponse(responseCode = "400", description = "Invalid request parameters"),
            @APIResponse(responseCode = "409", description = "Request not handled by this executor (request filter)"),
            @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response processRequest(
            @RequestBody(description = "Marketplace request details", required = true)
            @Valid MarketplaceRequestRes request) {
        useCasesService.processRequest(request);
        return Response.accepted().build();
    }
}
