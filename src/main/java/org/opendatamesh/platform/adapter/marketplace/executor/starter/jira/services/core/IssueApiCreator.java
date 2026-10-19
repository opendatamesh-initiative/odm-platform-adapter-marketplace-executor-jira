package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class IssueApiCreator implements IssueCreator {

    private static final int MAX_SUMMARY_LENGTH = 255;
    private static final String KEY_POINTER = "/key";
    private static final String ID_POINTER = "/id";

    private final JiraClient client;
    private final TicketTemplateRenderer renderer;
    private final JiraConfig config;
    private final AdfConverter adf;
    private final JiraUserService users;

    public IssueApiCreator(JiraClient client, TicketTemplateRenderer renderer, JiraConfig config, AdfConverter adf,
                           JiraUserService users) {
        this.client = client;
        this.renderer = renderer;
        this.config = config;
        this.adf = adf;
        this.users = users;
    }

    @Override
    public IssueInterfaceType type() {
        return IssueInterfaceType.ISSUE;
    }

    @Override
    public IssueReference create(JiraConfig.IssueProfile profile, Map<String, Object> data, MarketplaceRequest request) {
        ObjectNode fields = renderValues(profile, data);
        ObjectNode body = buildBody(profile, fields, request);
        JsonNode response = client.post(client.platformPath("/issue"), body);
        return toReference(response);
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

    private ObjectNode buildBody(JiraConfig.IssueProfile profile, ObjectNode fields, MarketplaceRequest request) {
        String uuid = request.identifier();
        fields.set("project", JsonNodeFactory.instance.objectNode().put("key", project(profile)));
        fields.set("issuetype", JsonNodeFactory.instance.objectNode().put("name", issueType(profile)));
        fields.put(config.correlation().field(), uuid);
        if (config.correlation().label()) {
            fields.set("labels", labelsWithCorrelation(fields, uuid));
        }
        if (profile.setReporter()) {
            fields.set("reporter", users.reporterField(users.resolveRequester(request.requester())));
        }
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.set("fields", fields);
        return body;
    }

    private ArrayNode labelsWithCorrelation(ObjectNode fields, String uuid) {
        ArrayNode labels = JsonNodeFactory.instance.arrayNode();
        JsonNode rendered = fields.get("labels");
        if (rendered != null && rendered.isArray()) {
            labels.addAll((ArrayNode) rendered);
        }
        labels.add("bdmp-" + uuid);
        return labels;
    }

    private IssueReference toReference(JsonNode response) {
        String key = response == null ? "" : response.at(KEY_POINTER).asText("");
        if (key.isBlank()) {
            throw new InternalException("Jira response did not contain the issue key at " + KEY_POINTER);
        }
        String id = response.at(ID_POINTER).asText("");
        return new IssueReference(key, id, client.browseLink(key));
    }

    private String project(JiraConfig.IssueProfile profile) {
        return profile.project().orElseThrow(() -> new InternalException("Issue API profile requires project and issue-type"));
    }

    private String issueType(JiraConfig.IssueProfile profile) {
        return profile.issueType().orElseThrow(() -> new InternalException("Issue API profile requires project and issue-type"));
    }
}
