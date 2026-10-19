package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

// BDMD-5340: Jira marketplace executor
class IssueApiCreatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private JiraClient client;
    private JiraUserService users;
    private JiraConfig config;
    private JiraConfig.IssueProfile profile;
    private final MarketplaceRequest request = new MarketplaceRequest("n", "id-1", new Provider("urn:dp:finance:sales", List.of("p1")),
            new Party("user", "a@b.c"), new Party("user", "jane@acme.com"), Map.of(), null, null);

    @BeforeEach
    void setUp() throws Exception {
        client = mock(JiraClient.class);
        when(client.platformPath(anyString())).thenAnswer(a -> "/rest/api/2" + a.getArgument(0));
        when(client.browseLink("ACC-1")).thenReturn("https://acme.atlassian.net/browse/ACC-1");
        when(client.post(anyString(), any())).thenReturn(mapper.readTree("{\"id\":\"10001\",\"key\":\"ACC-1\"}"));
        users = mock(JiraUserService.class);
        config = mock(JiraConfig.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(config.apiVersion()).thenReturn(2);
        when(config.correlation().field()).thenReturn("customfield_10050");
        when(config.correlation().label()).thenReturn(false);
        profile = mock(JiraConfig.IssueProfile.class);
        when(profile.project()).thenReturn(Optional.of("ACC"));
        when(profile.issueType()).thenReturn(Optional.of("Task"));
        when(profile.fields()).thenReturn(Map.of("summary", "Grant it", "description", "para one\n\npara two"));
        when(profile.objectFields()).thenReturn(Map.of("priority", Map.of("name", "High")));
        when(profile.listFields()).thenReturn(Map.of("labels", List.of("marketplace")));
        when(profile.richTextFields()).thenReturn(List.of("description"));
        when(profile.setReporter()).thenReturn(false);
    }

    private IssueApiCreator creator() {
        TicketTemplateRenderer renderer = mock(TicketTemplateRenderer.class);
        when(renderer.render(anyString(), any())).thenAnswer(a -> a.getArgument(0));
        return new IssueApiCreator(client, renderer, config, new AdfConverter(mapper), users);
    }

    private JsonNode sentBody() {
        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).post(eq("/rest/api/2/issue"), body.capture());
        return body.getValue();
    }

    @Test
    void whenTemplateOverridesCorrelationThenExecutorValueWins() {
        when(profile.fields()).thenReturn(Map.of("summary", "x", "customfield_10050", "forged", "project", "HACK"));

        IssueReference reference = creator().create(profile, Map.of(), request);

        JsonNode body = sentBody();
        assertEquals("id-1", body.at("/fields/customfield_10050").asText());
        assertEquals("ACC", body.at("/fields/project/key").asText());
        assertEquals("Task", body.at("/fields/issuetype/name").asText());
        assertEquals("High", body.at("/fields/priority/name").asText());
        assertEquals("ACC-1", reference.key());
        assertEquals("10001", reference.id());
        assertEquals("https://acme.atlassian.net/browse/ACC-1", reference.link());
    }

    @Test
    void whenCorrelationLabelEnabledThenLabelAdded() {
        when(config.correlation().label()).thenReturn(true);

        creator().create(profile, Map.of(), request);

        JsonNode labels = sentBody().at("/fields/labels");
        assertEquals("marketplace", labels.get(0).asText());
        assertEquals("bdmp-id-1", labels.get(1).asText());
    }

    @Test
    void whenSetReporterThenRequesterResolvedAndReporterSet() {
        when(profile.setReporter()).thenReturn(true);
        JiraUser jane = new JiraUser("acc-42", null);
        when(users.resolveRequester(request.requester())).thenReturn(jane);
        when(users.reporterField(jane)).thenReturn(mapper.createObjectNode().put("id", "acc-42"));

        creator().create(profile, Map.of(), request);

        assertEquals("acc-42", sentBody().at("/fields/reporter/id").asText());
    }

    /**
     * Scenario: REST v3 converts the description to ADF
     *   Given the connection uses REST API version 3 on Jira Cloud
     *   When an issue is created with a two-paragraph description
     *   Then the description is sent as an ADF document with two paragraphs
     */
    @Test
    void whenApiVersion3ThenRichTextFieldsSentAsAdf() {
        when(config.apiVersion()).thenReturn(3);

        creator().create(profile, Map.of(), request);

        JsonNode description = sentBody().at("/fields/description");
        assertEquals("doc", description.get("type").asText());
        assertEquals(2, description.get("content").size());
        assertTrue(sentBody().at("/fields/summary").isTextual());
    }

    @Test
    void whenSummaryTooLongThenTruncated() {
        when(profile.fields()).thenReturn(Map.of("summary", "s".repeat(400)));

        creator().create(profile, Map.of(), request);

        assertEquals(255, sentBody().at("/fields/summary").asText().length());
    }

    @Test
    void whenResponseHasNoKeyThenFails() throws Exception {
        when(client.post(anyString(), any())).thenReturn(mapper.readTree("{\"id\":\"10001\"}"));

        InternalException e = assertThrows(InternalException.class, () -> creator().create(profile, Map.of(), request));

        assertTrue(e.getMessage().contains("did not contain the issue key at /key"));
    }
}
