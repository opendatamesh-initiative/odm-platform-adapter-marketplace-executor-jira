package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// BDMD-5340: Jira marketplace executor
class ProcessMarketplaceRequestTest {

    private final List<MarketplaceResult> posted = new ArrayList<>();
    private final MarketplaceRequest request = new MarketplaceRequest("n", "id-3",
            new Provider("urn:dp:finance:sales", List.of("p1", "p2")), new Party("user", "a@b.c"), new Party("user", "a@b.c"),
            Map.of(), new Date(), null);

    /**
     * Scenario: Unknown requester fails the JSM request
     *   Given the unsubscribe profile uses the Service Management Request API
     *   And no Jira user matches "ghost@acme.com"
     *   When a MARKETPLACE_UNSUBSCRIBE request "id-3" requested by "ghost@acme.com" is processed
     *   Then no request is raised
     *   And an ERROR result stating that the requester was not found is posted for request "id-3"
     */
    @Test
    void whenIssueCreationFailsThenErrorReported() {
        JiraIssueOutboundPort failingIssues = (operation, req) -> {
            throw new InternalException("Requester ghost@acme.com not found in Jira, or ambiguous (0 matches)");
        };
        ResultMessageOutboundPort messages = new ResultMessageOutboundPort() {
            @Override
            public String pendingMessage(MarketplaceOperation operation, MarketplaceRequest req, IssueReference issue) {
                return "pending";
            }

            @Override
            public String technicalErrorMessage(MarketplaceOperation operation, MarketplaceRequest req, String error) {
                return "Technical error: " + error;
            }
        };
        MarketplaceResultOutboundPort results = (uuid, result) -> {
            assertEquals("id-3", uuid);
            posted.add(result);
        };

        new ProcessMarketplaceRequest(
                new ProcessMarketplaceRequestCommand(MarketplaceOperation.MARKETPLACE_UNSUBSCRIBE, request),
                new RequestFilterOutboundPort() {
                    @Override
                    public boolean isHandledByThisExecutor(MarketplaceOperation operation, MarketplaceRequest req) {
                        return true;
                    }

                    @Override
                    public String mismatchReason(MarketplaceOperation operation, MarketplaceRequest req) {
                        return "";
                    }
                },
                failingIssues, messages, results, Runnable::run).execute();

        assertEquals(1, posted.size());
        assertEquals(MarketplaceRequestStatus.ERROR, posted.get(0).status());
        assertTrue(posted.get(0).message().contains("not found"));
        assertEquals(List.of("p1", "p2"), posted.get(0).provider().dataProductPortsFqn());
    }
}
