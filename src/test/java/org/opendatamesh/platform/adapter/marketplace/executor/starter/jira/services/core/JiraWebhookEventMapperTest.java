package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class JiraWebhookEventMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private JiraConfig config;
    private JiraWebhookEventMapper eventMapper;

    @BeforeEach
    void setUp() {
        config = mock(JiraConfig.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(config.correlation().field()).thenReturn("customfield_10050");
        when(config.webhook().extraFields()).thenReturn(Map.of());
        eventMapper = new JiraWebhookEventMapper(config, new AdfConverter(mapper));
    }

    private JsonNode payload(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/jira/" + name)) {
            return mapper.readTree(in);
        }
    }

    @Test
    void whenCloudStatusTransitionThenNormalisedFields() throws Exception {
        TicketEvent event = eventMapper.toEvent(payload("cloud-issue-updated-status.json"));

        assertTrue(event.statusTransition());
        assertEquals("id-1", event.correlationId());
        assertEquals("ACC-1", event.fields().get("key"));
        assertEquals("10001", event.fields().get("id"));
        assertEquals("ACC", event.fields().get("project"));
        assertEquals("Task", event.fields().get("issueType"));
        assertEquals("Done", event.fields().get("status"));
        assertEquals("done", event.fields().get("statusCategory"));
        assertEquals("Done", event.fields().get("resolution"));
        assertEquals("Jane Doe", event.fields().get("actor"));
        assertEquals("Access granted in Snowflake", event.fields().get("lastComment"));
    }

    /**
     * Scenario: Data Center payload is mapped like Cloud
     *   When a recorded Jira Data Center "jira:issue_updated" payload with a status change is mapped
     *   Then the event carries the correlation, status, statusCategory, resolution and actor
     */
    @Test
    void whenDataCenterPayloadThenNormalisedFields() throws Exception {
        TicketEvent event = eventMapper.toEvent(payload("dc-issue-updated-status.json"));

        assertTrue(event.statusTransition());
        assertEquals("id-7", event.correlationId());
        assertEquals("Closed", event.fields().get("status"));
        assertEquals("done", event.fields().get("statusCategory"));
        assertEquals("Fixed", event.fields().get("resolution"));
        assertEquals("Jane Doe", event.fields().get("actor"));
        assertFalse(event.fields().containsKey("lastComment"));
    }

    @Test
    void whenNonStatusEditThenNotAStatusTransition() throws Exception {
        TicketEvent event = eventMapper.toEvent(payload("cloud-issue-updated-no-status.json"));

        assertFalse(event.statusTransition());
        assertEquals("", event.fields().get("resolution"));
        assertFalse(event.fields().containsKey("lastComment"));
    }

    @Test
    void whenCorrelationFieldMissingThenCorrelationNull() throws Exception {
        when(config.correlation().field()).thenReturn("customfield_99999");

        assertNull(eventMapper.toEvent(payload("cloud-issue-updated-status.json")).correlationId());
    }

    @Test
    void whenCommentBodyIsAdfThenFlattenedToText() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"user":{"displayName":"Jane Doe"},"issue":{"key":"ACC-2","fields":{"customfield_10050":" id-2 ",
                 "comment":{"comments":[{"body":{"type":"doc","version":1,"content":[
                   {"type":"paragraph","content":[{"type":"text","text":"first"}]},
                   {"type":"paragraph","content":[{"type":"text","text":"granted via ADF"}]}]}}]}}},
                 "changelog":{"items":[{"field":"status","fieldId":"status"}]}}
                """);

        TicketEvent event = eventMapper.toEvent(payload);

        assertEquals("id-2", event.correlationId());
        assertEquals("first\ngranted via ADF", event.fields().get("lastComment"));
    }

    @Test
    void whenExtraFieldsConfiguredThenReadByJsonPointer() throws Exception {
        when(config.webhook().extraFields()).thenReturn(Map.of("costCenter", "/issue/fields/customfield_20000", "missing", "/issue/nope"));
        JsonNode payload = mapper.readTree("{\"issue\":{\"fields\":{\"customfield_20000\":\"CC-9\"}},\"changelog\":{\"items\":[{\"fieldId\":\"status\"}]}}");

        TicketEvent event = eventMapper.toEvent(payload);

        assertEquals("CC-9", event.fields().get("costCenter"));
        assertEquals("", event.fields().get("missing"));
    }

    @Test
    void whenPayloadNotAnObjectThenEmptyNonTransitionEvent() throws Exception {
        TicketEvent event = eventMapper.toEvent(mapper.readTree("[]"));

        assertFalse(event.statusTransition());
        assertNull(event.correlationId());
        assertTrue(event.fields().isEmpty());
        assertFalse(eventMapper.toEvent(null).statusTransition());
    }
}
