package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraDeploymentType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class JiraUserService {

    private static final String MAX_RESULTS = "10";

    private final JiraClient client;
    private final JiraConfig config;

    public JiraUserService(JiraClient client, JiraConfig config) {
        this.client = client;
        this.config = config;
    }

    public JiraUser resolveRequester(Party requester) {
        requireUserParty(requester);
        String identifier = requester.identifier();
        List<JsonNode> results = searchUsers(identifier);
        JsonNode user = pickUser(results, identifier)
                .orElseThrow(() -> notFound(identifier, results.size()));
        return new JiraUser(user.path("accountId").asText(null), user.path("name").asText(null));
    }

    public ObjectNode reporterField(JiraUser user) {
        ObjectNode reporter = JsonNodeFactory.instance.objectNode();
        if (isCloud()) {
            reporter.put("id", user.accountId());
        } else {
            reporter.put("name", user.name());
        }
        return reporter;
    }

    public String onBehalfOf(JiraUser user) {
        return isCloud() ? user.accountId() : user.name();
    }

    private void requireUserParty(Party requester) {
        String type = requester == null ? null : requester.type();
        if (type == null || !"user".equalsIgnoreCase(type)) {
            throw new InternalException("Jira requests on behalf of the requester require a user requester; got " + type);
        }
    }

    private List<JsonNode> searchUsers(String identifier) {
        JsonNode response = isCloud()
                ? client.get(client.platformPath("/user/search"), Map.of("query", identifier, "maxResults", MAX_RESULTS))
                : client.get("/rest/api/2/user/search", Map.of("username", identifier, "maxResults", MAX_RESULTS));
        List<JsonNode> users = new ArrayList<>();
        if (response != null && response.isArray()) {
            response.forEach(users::add);
        }
        return users;
    }

    private Optional<JsonNode> pickUser(List<JsonNode> results, String identifier) {
        Optional<JsonNode> byEmail = results.stream()
                .filter(user -> identifier.equalsIgnoreCase(user.path("emailAddress").asText(null)))
                .findFirst();
        if (byEmail.isPresent()) {
            return byEmail;
        }
        List<JsonNode> active = results.stream().filter(user -> !user.path("active").asText("true").equals("false")).toList();
        return active.size() == 1 ? Optional.of(active.get(0)) : Optional.empty();
    }

    private InternalException notFound(String identifier, int matches) {
        return new InternalException("Requester " + identifier + " not found in Jira, or ambiguous (" + matches
                + " matches); on Cloud the user's email may be hidden: check the email visibility or the Browse users and groups permission");
    }

    private boolean isCloud() {
        return config.deployment() == JiraDeploymentType.CLOUD;
    }
}
