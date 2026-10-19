package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.IssueInterfaceType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AutomationWebhookCreator implements IssueCreator {

    private static final int MAX_SUMMARY_LENGTH = 255;
    private final JiraClient client;
    private final TicketTemplateRenderer renderer;
    private final JiraConfig config;
    private final AdfConverter adf;
    private final ObjectMapper objectMapper;

    public AutomationWebhookCreator(JiraClient client, TicketTemplateRenderer renderer, JiraConfig config, AdfConverter adf,
                                    ObjectMapper objectMapper) {
        this.client = client;
        this.renderer = renderer;
        this.config = config;
        this.adf = adf;
        this.objectMapper = objectMapper;
    }

    @Override
    public IssueInterfaceType type() {
        return IssueInterfaceType.AUTOMATION_WEBHOOK;
    }

    @Override
    public IssueReference create(JiraConfig.IssueProfile profile, Map<String, Object> data, MarketplaceRequest request) {
        ObjectNode fields = renderValues(profile, data);
        ObjectNode body = buildBody(fields, data, request.identifier());
        client.postAutomationWebhook(webhookUrl(profile), webhookToken(profile), body);
        return new IssueReference("", "", "");
    }

    private ObjectNode renderValues(JiraConfig.IssueProfile profile, Map<String, Object> data) {
        ObjectNode values = JsonNodeFactory.instance.objectNode();
        profile.fields().forEach((name, template) -> values.set(name, new TextNode(renderer.render(template, data))));
        profile.objectFields().forEach((name, entries) -> values.set(name, renderObject(entries, data)));
        profile.listFields().forEach((name, items) -> values.set(name, renderList(items, data)));
        convertRichTextToAdf(profile, values);
        truncateSummary(values);
        return values;
    }

    private ObjectNode renderObject(Map<String, String> entries, Map<String, Object> data) {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        entries.forEach((name, template) -> object.put(name, renderer.render(template, data)));
        return object;
    }

    private ArrayNode renderList(List<String> items, Map<String, Object> data) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        items.forEach(template -> array.add(renderer.render(template, data)));
        return array;
    }

    private void convertRichTextToAdf(JiraConfig.IssueProfile profile, ObjectNode values) {
        if (config.apiVersion() != 3) {
            return;
        }
        for (String name : profile.richTextFields()) {
            JsonNode value = values.get(name);
            if (value != null && value.isTextual()) {
                values.set(name, adf.toAdf(value.asText()));
            }
        }
    }

    private void truncateSummary(ObjectNode values) {
        JsonNode summary = values.get("summary");
        if (summary != null && summary.isTextual() && summary.asText().length() > MAX_SUMMARY_LENGTH) {
            values.put("summary", summary.asText().substring(0, MAX_SUMMARY_LENGTH));
        }
    }

    private ObjectNode buildBody(ObjectNode fields, Map<String, Object> data, String uuid) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("correlationId", uuid);
        body.put("operation", String.valueOf(data.get("operation")));
        body.set("request", objectMapper.valueToTree(data.get("request")));
        body.set("fields", fields);
        return body;
    }

    private String webhookUrl(JiraConfig.IssueProfile profile) {
        return profile.webhookUrl().orElseThrow(() -> new InternalException("Automation webhook profile requires webhook-url and webhook-token"));
    }

    private String webhookToken(JiraConfig.IssueProfile profile) {
        return profile.webhookToken().orElseThrow(() -> new InternalException("Automation webhook profile requires webhook-url and webhook-token"));
    }
}
