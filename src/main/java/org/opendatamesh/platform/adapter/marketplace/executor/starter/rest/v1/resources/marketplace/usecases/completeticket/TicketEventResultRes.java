package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.completeticket;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEventIgnoredReason;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Outcome of the processing of a Jira issue event")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketEventResultRes {

    @Schema(description = "REPORTED when a final result was posted to the Marketplace Service, IGNORED otherwise", example = "REPORTED")
    private String outcome;

    @Schema(description = "Final status reported to the Marketplace Service (only when outcome is REPORTED)")
    private MarketplaceRequestStatus status;

    @Schema(description = "Why the event was ignored (only when outcome is IGNORED)")
    private TicketEventIgnoredReason reason;

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public MarketplaceRequestStatus getStatus() {
        return status;
    }

    public void setStatus(MarketplaceRequestStatus status) {
        this.status = status;
    }

    public TicketEventIgnoredReason getReason() {
        return reason;
    }

    public void setReason(TicketEventIgnoredReason reason) {
        this.reason = reason;
    }
}
