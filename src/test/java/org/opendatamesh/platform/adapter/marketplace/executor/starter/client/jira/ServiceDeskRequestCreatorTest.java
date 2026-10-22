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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

// BDMD-5340: Jira marketplace executor
class ServiceDeskRequestCreatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private JiraClient client;
    private JiraUserService users;
    private JiraConfig config;
    private JiraConfig.IssueProfile profile;
    private final MarketplaceRequest request = new MarketplaceRequest("n", "id-4", new Provider("urn:dp:finance:sales", List.of("p1")),
            new Party("user", "a@b.c"), new Party("user", "jane@acme.com"), Map.of(), null, null);

    @BeforeEach
    void setUp() throws Exception {
        client = mock(JiraClient.class);
        when(client.browseLink("SD-7")).thenReturn("https://acme.atlassian.net/browse/SD-7");
        when(client.post(eq("/rest/servicedeskapi/request"), any(), anyString())).thenReturn(mapper.readTree("{\"issueId\":\"10007\",\"issueKey\":\"SD-7\"}"));
        users = mock(JiraUserService.class);
        JiraUser jane = new JiraUser("acc-42", null);
        when(users.resolveRequester(request.requester())).thenReturn(jane);
        when(users.onBehalfOf(jane)).thenReturn("acc-42");
        config = mock(JiraConfig.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(config.apiVersion()).thenReturn(2);
        when(config.correlation().field()).thenReturn("customfield_10050");
        when(config.correlation().label()).thenReturn(false);
        profile = mock(JiraConfig.IssueProfile.class);
        when(profile.serviceDeskId()).thenReturn(Optional.of("1"));
        when(profile.requestTypeId()).thenReturn(Optional.of("10"));
        when(profile.fields()).thenReturn(Map.of("summary", "Revoke it"));
        when(profile.objectFields()).thenReturn(Map.of());
        when(profile.listFields()).thenReturn(Map.of());
        when(profile.richTextFields()).thenReturn(List.of("description"));
        when(profile.correlationViaEdit()).thenReturn(false);
    }

    private ServiceDeskRequestCreator creator() {
        TicketTemplateRenderer renderer = mock(TicketTemplateRenderer.class);
        when(renderer.render(anyString(), any())).thenAnswer(a -> a.getArgument(0));
        return new ServiceDeskRequestCreator(client, renderer, config, new AdfConverter(mapper), users);
    }

    private JsonNode sentBody() {
        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).post(eq("/rest/servicedeskapi/request"), body.capture(), anyString());
        return body.getValue();
    }

    @Test
    void whenCreatedThenOnBehalfOfAndCorrelationSet() {
        IssueReference reference = creator().create(profile, Map.of(), request);

        JsonNode body = sentBody();
        assertEquals("acc-42", body.get("raiseOnBehalfOf").asText());
        assertEquals("1", body.get("serviceDeskId").asText());
        assertEquals("10", body.get("requestTypeId").asText());
        assertEquals("id-4", body.at("/requestFieldValues/customfield_10050").asText());
        assertFalse(body.get("isAdfRequest").asBoolean());
        assertEquals("SD-7", reference.key());
        verify(client, never()).putIssueFields(any(), any());
    }

    /**
     * Scenario: JSM correlation set by a follow-up edit
     *   Given the profile enables correlation-via-edit
     *   When a JSM request "SD-7" is created for request "id-4"
     *   Then the correlation field of "SD-7" is updated to "id-4"
     *   And a PENDING result mentioning "SD-7" is posted
     */
    @Test
    void whenCorrelationViaEditThenIssueFieldUpdated() {
        when(profile.correlationViaEdit()).thenReturn(true);

        IssueReference reference = creator().create(profile, Map.of(), request);

        assertEquals("SD-7", reference.key());
        assertTrue(sentBody().at("/requestFieldValues/customfield_10050").isMissingNode());
        ArgumentCaptor<ObjectNode> fields = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).putIssueFields(eq("SD-7"), fields.capture());
        assertEquals("id-4", fields.getValue().get("customfield_10050").asText());
    }

    @Test
    void whenCorrelationEditFailsThenDescriptiveErrorNamesTheRequest() {
        when(profile.correlationViaEdit()).thenReturn(true);
        doThrow(new InternalException("Jira call PUT failed with HTTP 400: field not on screen")).when(client).putIssueFields(any(), any());

        InternalException e = assertThrows(InternalException.class, () -> creator().create(profile, Map.of(), request));

        assertTrue(e.getMessage().startsWith("Jira request SD-7 was created but the correlation field could not be set: "));
        assertTrue(e.getMessage().endsWith("; the request will not be completed automatically"));
    }

    /**
     * Scenario: Unknown requester fails the JSM request
     *   Given the unsubscribe profile uses the Service Management Request API
     *   And no Jira user matches "ghost@acme.com"
     *   When a MARKETPLACE_UNSUBSCRIBE request "id-3" requested by "ghost@acme.com" is processed
     *   Then no request is raised
     *   And an ERROR result stating that the requester was not found is posted for request "id-3"
     */
    @Test
    void whenRequesterNotFoundThenNoRequestRaised() {
        when(users.resolveRequester(any())).thenThrow(new InternalException("Requester ghost@acme.com not found in Jira, or ambiguous (0 matches)"));

        assertThrows(InternalException.class, () -> creator().create(profile, Map.of(), request));

        verify(client, never()).post(anyString(), any(), any());
    }

    @Test
    void whenApiVersion3ThenAdfRequestFlagSet() {
        when(config.apiVersion()).thenReturn(3);
        when(profile.fields()).thenReturn(Map.of("summary", "x", "description", "d"));

        creator().create(profile, Map.of(), request);

        assertTrue(sentBody().get("isAdfRequest").asBoolean());
        assertEquals("doc", sentBody().at("/requestFieldValues/description/type").asText());
    }
}
