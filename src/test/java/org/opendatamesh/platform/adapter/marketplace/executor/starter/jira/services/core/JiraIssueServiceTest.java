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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

// BDMD-5340: Jira marketplace executor
class JiraIssueServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JiraClient client = mock(JiraClient.class);
    private final JiraIssueService service = new JiraIssueService(mock(JiraConfig.class), mock(TicketTemplateRenderer.class),
            instances(), client, new AdfConverter(mapper));

    @SuppressWarnings("unchecked")
    private static jakarta.enterprise.inject.Instance<IssueCreator> instances() {
        jakarta.enterprise.inject.Instance<IssueCreator> instance = mock(jakarta.enterprise.inject.Instance.class);
        return instance;
    }

    /**
     * Scenario: Last comment failure does not block the result
     *   Given reading the issue comments fails with HTTP 500
     *   When the native done event for "id-1" is processed
     *   Then a GRANTED result is still posted without a comment
     */
    @Test
    void whenCommentReadFailsThenFieldsReturnedUnchanged() {
        when(client.platformPath(anyString())).thenAnswer(a -> "/rest/api/2" + a.getArgument(0));
        when(client.get(anyString(), any())).thenThrow(new InternalException("Jira call GET failed with HTTP 500: boom"));
        Map<String, String> fields = Map.of("key", "ACC-1", "statusCategory", "done");

        Map<String, String> result = service.withLastComment(fields);

        assertSame(fields, result);
    }

    @Test
    void whenLastCommentPresentThenNoCallMade() {
        Map<String, String> fields = Map.of("key", "ACC-1", "lastComment", "already here");

        assertSame(fields, service.withLastComment(fields));
        verifyNoInteractions(client);
    }

    @Test
    void whenCommentFoundThenAdded() throws Exception {
        when(client.platformPath(anyString())).thenAnswer(a -> "/rest/api/2" + a.getArgument(0));
        when(client.get(eq("/rest/api/2/issue/ACC-1/comment"), any()))
                .thenReturn(mapper.readTree("{\"comments\":[{\"body\":\"Access granted in Snowflake\"}]}"));

        Map<String, String> result = service.withLastComment(Map.of("key", "ACC-1"));

        assertEquals("Access granted in Snowflake", result.get("lastComment"));
    }

    @Test
    void whenAdfCommentThenFlattenedToText() throws Exception {
        when(client.platformPath(anyString())).thenAnswer(a -> "/rest/api/3" + a.getArgument(0));
        when(client.get(anyString(), any())).thenReturn(mapper.readTree("""
                {"comments":[{"body":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"ADF says yes"}]}]}}]}
                """));

        assertEquals("ADF says yes", service.withLastComment(Map.of("key", "ACC-1")).get("lastComment"));
    }

    @Test
    void whenNoCommentsThenEmptyLastComment() throws Exception {
        when(client.platformPath(anyString())).thenAnswer(a -> "/rest/api/2" + a.getArgument(0));
        when(client.get(anyString(), any())).thenReturn(mapper.readTree("{\"comments\":[]}"));

        assertEquals("", service.withLastComment(Map.of("key", "ACC-1")).get("lastComment"));
    }
}
