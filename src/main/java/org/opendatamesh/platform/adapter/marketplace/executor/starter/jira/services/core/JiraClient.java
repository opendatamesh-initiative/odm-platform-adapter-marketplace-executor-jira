package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraApi;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraDeploymentType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientResourceMappingException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ApplicationScoped
public class JiraClient {

    private static final int MAX_BODY_LENGTH = 500;
    private static final String ATLASSIAN_TOKEN_URL = "https://auth.atlassian.com/oauth/token";

    private final RestUtils jiraRestUtils;
    private final RestUtils plainRestUtils;
    private final JiraConfig config;
    private final ObjectMapper objectMapper;

    public JiraClient(@JiraApi RestUtils jiraRestUtils, RestUtils plainRestUtils, JiraConfig config, ObjectMapper objectMapper) {
        this.jiraRestUtils = jiraRestUtils;
        this.plainRestUtils = plainRestUtils;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public String platformPath(String suffix) {
        return "/rest/api/" + config.apiVersion() + suffix;
    }

    public JsonNode post(String path, ObjectNode body) {
        return post(path, body, null);
    }

    /** {@code clientErrorHint} is appended to the message of a 400 or 403 answer. */
    public JsonNode post(String path, ObjectNode body, String clientErrorHint) {
        String url = config.apiBaseUrl() + path;
        return translateErrors("POST", path, clientErrorHint,
                () -> retryWhenRateLimited(() -> jiraRestUtils.genericPost(url, headers(), body, JsonNode.class)));
    }

    public void putIssueFields(String issueKey, ObjectNode fields) {
        String path = platformPath("/issue/{id}");
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.set("fields", fields);
        translateErrors("PUT", path, null, () -> jiraRestUtils.put(config.apiBaseUrl() + path, headers(), issueKey, body, JsonNode.class));
    }

    public JsonNode get(String path, Map<String, String> queryParams) {
        String url = config.apiBaseUrl() + path + queryString(queryParams);
        return translateErrors("GET", path, null, () -> jiraRestUtils.genericGet(url, headers(), null, JsonNode.class));
    }

    public void postAutomationWebhook(String webhookUrl, String token, ObjectNode body) {
        List<HttpHeader> webhookHeaders = new ArrayList<>(headers());
        webhookHeaders.add(new HttpHeader("X-Automation-Webhook-Token", token));
        try {
            plainRestUtils.genericPost(webhookUrl, webhookHeaders, body, JsonNode.class);
        } catch (ClientException e) {
            throw automationError(e);
        } catch (ClientResourceMappingException e) {
            throw new InternalException("Unexpected Jira Automation webhook response", e);
        }
    }

    public String browseLink(String key) {
        if (isBlank(key)) {
            return "";
        }
        return stripTrailingSlash(config.siteUrl()) + "/browse/" + key;
    }

    private JsonNode retryWhenRateLimited(Supplier<JsonNode> call) {
        int maxAttempts = Math.max(1, config.rateLimit().maxAttempts());
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (ClientException e) {
                if (e.getCode() != 429 || attempt >= maxAttempts) {
                    throw e;
                }
                pauseBeforeRetry(attempt);
            }
        }
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(config.rateLimit().backoff().toMillis() * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("Interrupted while waiting to retry a rate-limited Jira call", e);
        }
    }

    private JsonNode translateErrors(String method, String path, String clientErrorHint, Supplier<JsonNode> call) {
        try {
            return call.get();
        } catch (ClientException e) {
            throw clientError(e, method, path, clientErrorHint);
        } catch (ClientResourceMappingException e) {
            throw new InternalException("Unexpected Jira response for " + path, e);
        } catch (IllegalStateException e) {
            throw new InternalException("Unable to obtain a Jira OAuth token from " + tokenUrl() + ": " + e.getMessage()
                    + ". Check the service account client credentials and scopes", e);
        }
    }

    private InternalException clientError(ClientException e, String method, String path, String clientErrorHint) {
        if (e.getCode() == 401 || e.getCode() == 403) {
            return new InternalException("Jira rejected the integration credentials (HTTP " + e.getCode()
                    + ", auth type " + config.auth().type() + ", deployment " + config.deployment() + ") for " + path
                    + ": check the service account or token, its product access and the project permissions"
                    + hint(e, clientErrorHint));
        }
        return new InternalException("Jira call " + method + " " + path + " failed with HTTP " + e.getCode()
                + ": " + jiraErrors(e.getResponseBody()) + hint(e, clientErrorHint));
    }

    private InternalException automationError(ClientException e) {
        if (e.getCode() == 401 || e.getCode() == 403) {
            return new InternalException("Jira Automation webhook rejected the call (HTTP " + e.getCode()
                    + "): check the webhook token and the rule's incoming webhook trigger");
        }
        return new InternalException("Jira Automation webhook call failed with HTTP " + e.getCode()
                + ": " + jiraErrors(e.getResponseBody()));
    }

    private String hint(ClientException e, String clientErrorHint) {
        boolean applicable = e.getCode() == 400 || e.getCode() == 403;
        return applicable && clientErrorHint != null ? "; " + clientErrorHint : "";
    }

    private String jiraErrors(String body) {
        if (body == null) {
            return "";
        }
        String errors = readJiraErrors(body);
        String text = errors.isEmpty() ? body : errors;
        return text.length() <= MAX_BODY_LENGTH ? text : text.substring(0, MAX_BODY_LENGTH);
    }

    private String readJiraErrors(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            List<String> messages = new ArrayList<>();
            json.path("errorMessages").forEach(message -> messages.add(message.asText()));
            json.path("errors").fields().forEachRemaining(entry -> messages.add(entry.getKey() + ": " + entry.getValue().asText()));
            if (json.hasNonNull("errorMessage")) {
                messages.add(json.get("errorMessage").asText());
            }
            return messages.stream().collect(Collectors.joining("; "));
        } catch (Exception e) {
            return "";
        }
    }

    private String tokenUrl() {
        return config.auth().oauth2().tokenUrl().orElse(
                config.deployment() == JiraDeploymentType.CLOUD
                        ? ATLASSIAN_TOKEN_URL
                        : stripTrailingSlash(config.siteUrl()) + "/rest/oauth2/latest/token");
    }

    private List<HttpHeader> headers() {
        return List.of(
                new HttpHeader("Content-Type", "application/json"),
                new HttpHeader("Accept", "application/json")
        );
    }

    private String queryString(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        return "?" + queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
