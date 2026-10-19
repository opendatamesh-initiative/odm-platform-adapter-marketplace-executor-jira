package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.jboss.logging.Logger;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.IssueInterfaceType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class JiraIssueService {

    private static final Logger log = Logger.getLogger(JiraIssueService.class);

    private final JiraConfig config;
    private final TicketTemplateRenderer renderer;
    private final Map<IssueInterfaceType, IssueCreator> creators = new EnumMap<>(IssueInterfaceType.class);
    private final JiraClient client;
    private final AdfConverter adf;

    public JiraIssueService(JiraConfig config, TicketTemplateRenderer renderer, Instance<IssueCreator> creators,
                            JiraClient client, AdfConverter adf) {
        this.config = config;
        this.renderer = renderer;
        creators.forEach(creator -> this.creators.put(creator.type(), creator));
        this.client = client;
        this.adf = adf;
    }

    public IssueReference openIssue(MarketplaceOperation operation, MarketplaceRequest request) {
        JiraConfig.IssueProfile profile = config.profiles().forOperation(operation);
        Map<String, Object> data = renderer.requestData(operation, request);
        IssueReference issue = creatorFor(profile).create(profile, data, request);
        log.infof("Opened Jira issue %s for request %s operation %s",
                issue.key().isBlank() ? "via automation" : issue.key(), request.identifier(), operation);
        return issue;
    }

    public Map<String, String> withLastComment(Map<String, String> fields) {
        String key = fields.get("key");
        if (fields.containsKey("lastComment") || key == null || key.isBlank()) {
            return fields;
        }
        try {
            Map<String, String> enriched = new LinkedHashMap<>(fields);
            enriched.put("lastComment", readLastComment(key));
            return enriched;
        } catch (RuntimeException e) {
            log.warnf("Last comment of Jira issue %s not read: %s", key, e.getMessage());
            return fields;
        }
    }

    private String readLastComment(String key) {
        JsonNode response = client.get(client.platformPath("/issue/" + urlEncode(key) + "/comment"),
                Map.of("orderBy", "-created", "maxResults", "1"));
        JsonNode body = response == null ? null : response.at("/comments/0/body");
        if (body == null || body.isMissingNode() || body.isNull()) {
            return "";
        }
        return body.isTextual() ? body.asText() : adf.toText(body);
    }

    private IssueCreator creatorFor(JiraConfig.IssueProfile profile) {
        IssueCreator creator = creators.get(profile.type());
        if (creator == null) {
            throw new InternalException("No issue creator available for interface type " + profile.type());
        }
        return creator;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
