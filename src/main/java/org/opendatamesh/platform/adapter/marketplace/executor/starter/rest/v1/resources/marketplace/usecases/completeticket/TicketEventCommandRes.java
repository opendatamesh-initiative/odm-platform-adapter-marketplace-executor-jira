package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.completeticket;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Ticket event sent by Jira when an issue changes status")
public class TicketEventCommandRes {

    @Schema(description = "Marketplace request UUID stored in the Jira correlation custom field")
    private String correlationId;

    @Schema(description = "Normalised issue fields used by outcome rules and messages: key, id, project, issueType, status, statusCategory, resolution, actor, lastComment")
    private Map<String, Object> fields;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
