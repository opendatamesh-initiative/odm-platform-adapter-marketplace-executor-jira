package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.MarketplaceExecutorUseCasesService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.completeticket.TicketEventCommandRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.completeticket.TicketEventResultRes;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/up/marketplace-executor/webhooks/jira")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Jira Issue Events", description = "Completion notifications sent by Jira (through the blindata-agent proxy) when an issue changes status")
public class JiraIssueEventUseCaseController {

    private final MarketplaceExecutorUseCasesService useCasesService;

    public JiraIssueEventUseCaseController(MarketplaceExecutorUseCasesService useCasesService) {
        this.useCasesService = useCasesService;
    }

    @POST
    @Path("/issue-events")
    @Operation(summary = "Receive a Jira Automation issue event", description = "Reports the final result of the marketplace request when the issue reached a terminal state, otherwise ignores the event")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Event reported to the Marketplace Service or ignored"),
            @APIResponse(responseCode = "500", description = "Marketplace or Jira processing failure, Jira Automation does not retry")
    })
    public TicketEventResultRes receiveIssueEvent(
            @RequestBody(description = "Issue event details sent by the Automation rule", required = true) TicketEventCommandRes body) {
        return useCasesService.processTicketEvent(body);
    }

    @POST
    @Path("/native-events")
    @Operation(summary = "Receive a native Jira webhook event", description = "Maps the native Jira webhook payload (jira:issue_updated) to an issue event, reports the final result of the marketplace request when the status transition reached a terminal state, otherwise ignores the event")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Event reported to the Marketplace Service or ignored"),
            @APIResponse(responseCode = "500", description = "Marketplace or Jira processing failure, native webhooks are retried by Jira")
    })
    public TicketEventResultRes receiveNativeEvent(
            @RequestBody(description = "Native Jira webhook payload, a JSON object", required = true) ObjectNode payload) {
        return useCasesService.processJiraWebhookEvent(payload);
    }
}
