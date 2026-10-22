package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.AdfConverter;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class JiraWebhookEventMapper {

    private final JiraConfig config;
    private final AdfConverter adf;

    public JiraWebhookEventMapper(JiraConfig config, AdfConverter adf) {
        this.config = config;
        this.adf = adf;
    }

    public TicketEvent toEvent(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return new TicketEvent(null, Map.of(), false);
        }
        return new TicketEvent(correlationId(payload), normalisedFields(payload), isStatusTransition(payload));
    }

    private boolean isStatusTransition(JsonNode payload) {
        for (JsonNode item : payload.at("/changelog/items")) {
            if ("status".equalsIgnoreCase(item.path("fieldId").asText(""))
                    || "status".equalsIgnoreCase(item.path("field").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private String correlationId(JsonNode payload) {
        String value = text(payload.at("/issue/fields/" + config.correlation().field())).trim();
        return value.isEmpty() ? null : value;
    }

    private Map<String, String> normalisedFields(JsonNode payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", text(payload.at("/issue/key")));
        fields.put("id", text(payload.at("/issue/id")));
        fields.put("project", text(payload.at("/issue/fields/project/key")));
        fields.put("issueType", text(payload.at("/issue/fields/issuetype/name")));
        fields.put("status", text(payload.at("/issue/fields/status/name")));
        fields.put("statusCategory", text(payload.at("/issue/fields/status/statusCategory/key")));
        fields.put("resolution", text(payload.at("/issue/fields/resolution/name")));
        fields.put("actor", text(payload.at("/user/displayName")));
        lastComment(payload).ifPresent(comment -> fields.put("lastComment", comment));
        config.webhook().extraFields().forEach((name, pointer) -> fields.put(name, text(payload.at(pointer))));
        return fields;
    }

    private Optional<String> lastComment(JsonNode payload) {
        JsonNode comments = payload.at("/issue/fields/comment/comments");
        if (!comments.isArray()) {
            return java.util.Optional.empty();
        }
        if (comments.isEmpty()) {
            return java.util.Optional.of("");
        }
        return java.util.Optional.of(text(comments.get(comments.size() - 1).path("body")));
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.isValueNode() ? node.asText() : adf.toText(node);
    }
}
