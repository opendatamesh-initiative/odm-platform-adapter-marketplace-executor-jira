package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

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
public class ServiceDeskRequestCreator implements IssueCreator {

    private static final int MAX_SUMMARY_LENGTH = 255;
    private static final String KEY_POINTER = "/issueKey";
    private static final String ID_POINTER = "/issueId";

    private final JiraClient client;
    private final TicketTemplateRenderer renderer;
    private final JiraConfig config;
    private final AdfConverter adf;
    private final JiraUserService users;

    public ServiceDeskRequestCreator(JiraClient client, TicketTemplateRenderer renderer, JiraConfig config, AdfConverter adf,
                                     JiraUserService users) {
        this.client = client;
        this.renderer = renderer;
        this.config = config;
        this.adf = adf;
        this.users = users;
    }

    @Override
    public IssueInterfaceType type() {
        return IssueInterfaceType.SERVICE_DESK_REQUEST;
    }

    @Override
    public IssueReference create(JiraConfig.IssueProfile profile, Map<String, Object> data, MarketplaceRequest request) {
        ObjectNode values = renderValues(profile, data);
        String onBehalfOf = users.onBehalfOf(users.resolveRequester(request.requester()));
        ObjectNode body = buildBody(profile, values, request.identifier(), onBehalfOf);
        JsonNode response = client.post("/rest/servicedeskapi/request", body, customerHint(profile));
        IssueReference reference = toReference(response);
        setCorrelationByEdit(profile, reference, request.identifier());
        return reference;
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

    private ObjectNode buildBody(JiraConfig.IssueProfile profile, ObjectNode values, String uuid, String onBehalfOf) {
        if (!profile.correlationViaEdit()) {
            values.put(config.correlation().field(), uuid);
        }
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("serviceDeskId", serviceDeskId(profile));
        body.put("requestTypeId", requestTypeId(profile));
        body.set("requestFieldValues", values);
        body.put("raiseOnBehalfOf", onBehalfOf);
        body.put("isAdfRequest", config.apiVersion() == 3);
        return body;
    }

    private IssueReference toReference(JsonNode response) {
        String key = response == null ? "" : response.at(KEY_POINTER).asText("");
        if (key.isBlank()) {
            throw new InternalException("Jira response did not contain the issue key at " + KEY_POINTER);
        }
        String id = response.at(ID_POINTER).asText("");
        return new IssueReference(key, id, client.browseLink(key));
    }

    private void setCorrelationByEdit(JiraConfig.IssueProfile profile, IssueReference reference, String uuid) {
        if (!profile.correlationViaEdit()) {
            return;
        }
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        fields.put(config.correlation().field(), uuid);
        if (config.correlation().label()) {
            fields.set("labels", JsonNodeFactory.instance.arrayNode().add("bdmp-" + uuid));
        }
        try {
            client.putIssueFields(reference.key(), fields);
        } catch (RuntimeException e) {
            throw new InternalException("Jira request " + reference.key() + " was created but the correlation field could not be set: "
                    + e.getMessage() + "; the request will not be completed automatically", e);
        }
    }

    private String customerHint(JiraConfig.IssueProfile profile) {
        return "the requester must be a customer of service desk " + serviceDeskId(profile)
                + " and the integration identity a service desk agent";
    }

    private String serviceDeskId(JiraConfig.IssueProfile profile) {
        return profile.serviceDeskId().orElseThrow(() -> new InternalException("Service desk profile requires service-desk-id and request-type-id"));
    }

    private String requestTypeId(JiraConfig.IssueProfile profile) {
        return profile.requestTypeId().orElseThrow(() -> new InternalException("Service desk profile requires service-desk-id and request-type-id"));
    }
}
