package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

// BDMD-5340: Jira marketplace executor
class AutomationWebhookCreatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Scenario: Automation webhook kind reports PENDING without an issue key
     *   Given the subscribe profile uses an Automation Incoming Webhook
     *   When a subscribe request "id-5" is processed
     *   Then the webhook is called with the automation token header and correlationId "id-5"
     *   And no Jira credentials are sent to the webhook
     *   And a PENDING result stating the request was submitted to the Jira Automation rule is posted
     */
    @Test
    void whenAutomationKindThenTokenSentAndEmptyReference() {
        JiraClient client = mock(JiraClient.class);
        JiraConfig config = mock(JiraConfig.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(config.apiVersion()).thenReturn(2);
        TicketTemplateRenderer renderer = mock(TicketTemplateRenderer.class);
        when(renderer.render(anyString(), any())).thenAnswer(a -> a.getArgument(0));
        JiraConfig.IssueProfile profile = mock(JiraConfig.IssueProfile.class);
        when(profile.webhookUrl()).thenReturn(Optional.of("https://automation.example.test/pro/hook"));
        when(profile.webhookToken()).thenReturn(Optional.of("tok"));
        when(profile.fields()).thenReturn(Map.of("summary", "Grant it"));
        when(profile.objectFields()).thenReturn(Map.of());
        when(profile.listFields()).thenReturn(Map.of());
        when(profile.richTextFields()).thenReturn(List.of());
        MarketplaceRequest request = new MarketplaceRequest("n", "id-5", new Provider("urn:dp:finance:sales", List.of("p1")),
                new Party("user", "a@b.c"), new Party("user", "a@b.c"), Map.of(), null, null);
        Map<String, Object> data = Map.of("operation", "MARKETPLACE_SUBSCRIBE", "request", Map.of("identifier", "id-5"));

        IssueReference reference = new AutomationWebhookCreator(client, renderer, config, new AdfConverter(mapper), mapper)
                .create(profile, data, request);

        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).postAutomationWebhook(eq("https://automation.example.test/pro/hook"), eq("tok"), body.capture());
        assertEquals("id-5", body.getValue().get("correlationId").asText());
        assertEquals("MARKETPLACE_SUBSCRIBE", body.getValue().get("operation").asText());
        assertEquals("id-5", body.getValue().at("/request/identifier").asText());
        assertEquals("Grant it", body.getValue().at("/fields/summary").asText());
        // the Jira-authenticated client is never used by this kind
        verify(client, never()).post(anyString(), any());
        assertEquals("", reference.key());
        assertEquals("", reference.id());
        assertEquals("", reference.link());
    }
}
