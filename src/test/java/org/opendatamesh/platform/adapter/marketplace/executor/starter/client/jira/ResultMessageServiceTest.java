package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Expression;
import io.quarkus.qute.TemplateNode.Origin;
import io.quarkus.qute.ResultMapper;
import io.quarkus.qute.Results;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcomeCause;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class ResultMessageServiceTest {

    private static final String TEMPLATE =
            "{outcome.status} via {ticket.key} [{ticket.link}] by:{ticket.closedBy}|{fields.actor}|{fields.lastComment}";

    /**
     * Scenario: Actor is omitted when closing-user inclusion is disabled
     *   Given closing-user inclusion is disabled
     *   When a fulfilled issue event with actor "Jane Doe" is processed
     *   Then the result message does not contain "Jane Doe"
     */
    @Test
    void whenClosingUserExcludedThenNotExposedToTemplates() {
        Map<String, String> fields = Map.of("key", "ACC-1", "id", "10001", "actor", "Jane Doe", "lastComment", "done");

        String excluded = finalMessage(false, fields);
        String included = finalMessage(true, fields);

        assertFalse(excluded.contains("Jane Doe"));
        assertEquals("GRANTED via ACC-1 [https://acme.atlassian.net/browse/ACC-1] by:||done", excluded);
        assertTrue(included.contains("by:Jane Doe|Jane Doe|done"));
    }

    private String finalMessage(boolean includeClosingUser, Map<String, String> fields) {
        ResultMessageService service = new ResultMessageService(config(includeClosingUser),
                new TicketTemplateRenderer(engine()), client());
        return service.finalOutcome(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, request(),
                new TicketOutcome(TicketOutcomeCause.FULFILLED, null), MarketplaceRequestStatus.GRANTED, fields);
    }

    private Engine engine() {
        // equivalent of quarkus.qute.property-not-found-strategy=noop
        return Engine.builder().addDefaults().strictRendering(false)
                .addResultMapper(new ResultMapper() {
                    @Override
                    public boolean appliesTo(Origin origin, Object result) {
                        return Results.isNotFound(result);
                    }

                    @Override
                    public String map(Object result, Expression expression) {
                        return "";
                    }
                }).build();
    }

    private JiraClient client() {
        JiraClient client = mock(JiraClient.class);
        when(client.browseLink("ACC-1")).thenReturn("https://acme.atlassian.net/browse/ACC-1");
        return client;
    }

    private JiraConfig config(boolean includeClosingUser) {
        JiraConfig config = mock(JiraConfig.class, RETURNS_DEEP_STUBS);
        JiraConfig.ProfileMessages profileMessages = mock(JiraConfig.ProfileMessages.class);
        when(profileMessages.fulfilled()).thenReturn(Optional.empty());
        JiraConfig.IssueProfile profile = mock(JiraConfig.IssueProfile.class);
        when(profile.messages()).thenReturn(profileMessages);
        when(config.profiles().forOperation(MarketplaceOperation.MARKETPLACE_SUBSCRIBE)).thenReturn(profile);
        when(config.messages().fulfilled()).thenReturn(TEMPLATE);
        when(config.messages().closingUser().include()).thenReturn(includeClosingUser);
        when(config.messages().closingUser().field()).thenReturn("actor");
        return config;
    }

    private MarketplaceRequest request() {
        return new MarketplaceRequest("req", "id-1", new Provider("urn:dp:finance:sales", List.of("p1")),
                new Party("USER", "c"), new Party("USER", "r"), Map.of(), null, null);
    }
}
