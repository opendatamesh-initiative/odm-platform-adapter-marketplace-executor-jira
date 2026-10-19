package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraAuthType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraDeploymentType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class JiraClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private RestUtils jiraRestUtils;
    private RestUtils plainRestUtils;
    private JiraClient client;
    private ObjectNode body;

    @BeforeEach
    void setUp() {
        jiraRestUtils = mock(RestUtils.class);
        plainRestUtils = mock(RestUtils.class);
        JiraConfig config = mock(JiraConfig.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(config.deployment()).thenReturn(JiraDeploymentType.CLOUD);
        when(config.siteUrl()).thenReturn("https://acme.atlassian.net/");
        when(config.apiBaseUrl()).thenReturn("https://acme.atlassian.net");
        when(config.apiVersion()).thenReturn(2);
        when(config.auth().type()).thenReturn(JiraAuthType.API_TOKEN);
        when(config.auth().oauth2().tokenUrl()).thenReturn(java.util.Optional.of("https://auth.example.test/token"));
        when(config.rateLimit().maxAttempts()).thenReturn(3);
        when(config.rateLimit().backoff()).thenReturn(Duration.ofMillis(5));
        client = new JiraClient(jiraRestUtils, plainRestUtils, config, mapper);
        body = mapper.createObjectNode();
    }

    /**
     * Scenario: Jira rejects the integration credentials
     *   Given Jira answers 401 to issue creation
     *   When a subscribe request "id-6" is processed
     *   Then an ERROR result mentioning the integration credentials is posted for request "id-6"
     *   And no secret value appears in the message
     */
    @Test
    void whenUnauthorizedThenDescriptiveErrorWithoutSecrets() {
        when(jiraRestUtils.genericPost(anyString(), any(), any(), any()))
                .thenThrow(new ClientException(401, "{\"error\":\"secret-body-token\"}"));

        InternalException e = assertThrows(InternalException.class, () -> client.post("/rest/api/2/issue", body));

        assertTrue(e.getMessage().contains("integration credentials"));
        assertTrue(e.getMessage().contains("API_TOKEN"));
        assertTrue(e.getMessage().contains("CLOUD"));
        assertFalse(e.getMessage().contains("secret-body-token"));
    }

    /**
     * Scenario: OAuth token cannot be obtained
     *   Given the OAuth token endpoint rejects the client credentials
     *   When a subscribe request "id-7" is processed
     *   Then an ERROR result explaining that a Jira OAuth token could not be obtained is posted for request "id-7"
     */
    @Test
    void whenOauthTokenUnavailableThenDescriptiveError() {
        when(jiraRestUtils.genericGet(anyString(), any(), any(), any())).thenThrow(new IllegalStateException("invalid_client"));

        InternalException e = assertThrows(InternalException.class,
                () -> client.get("/rest/api/2/user/search", Map.of("query", "a@b.c")));

        assertTrue(e.getMessage().contains("Unable to obtain a Jira OAuth token from https://auth.example.test/token"));
        assertTrue(e.getMessage().contains("invalid_client"));
    }

    /**
     * Scenario: Jira rejects the issue fields
     *   Given Jira answers 400 with an errors map
     *   When an issue is created
     *   Then the error message includes the Jira field errors
     */
    @Test
    void whenBadRequestThenJiraErrorsIncluded() {
        when(jiraRestUtils.genericPost(anyString(), any(), any(), any()))
                .thenThrow(new ClientException(400, "{\"errorMessages\":[],\"errors\":{\"customfield_10050\":\"Field is not on the screen\"}}"));

        InternalException e = assertThrows(InternalException.class, () -> client.post("/rest/api/2/issue", body));

        assertTrue(e.getMessage().contains("HTTP 400"));
        assertTrue(e.getMessage().contains("customfield_10050: Field is not on the screen"));
    }

    /**
     * Scenario: Rate-limited creation is retried
     *   Given Jira answers 429 once and then creates issue "ACC-9"
     *   When a subscribe request "id-7" is processed
     *   Then a PENDING result mentioning "ACC-9" is posted
     */
    @Test
    void whenRateLimitedOnceThenRetriedAndSucceeds() throws Exception {
        JsonNode created = mapper.readTree("{\"key\":\"ACC-9\"}");
        when(jiraRestUtils.genericPost(anyString(), any(), any(), any()))
                .thenThrow(new ClientException(429, "slow down"))
                .thenReturn(created);

        JsonNode response = client.post("/rest/api/2/issue", body);

        assertEquals("ACC-9", response.get("key").asText());
        verify(jiraRestUtils, times(2)).genericPost(anyString(), any(), any(), any());
    }

    /**
     * Scenario: Rate limit retries are bounded
     *   Given Jira keeps answering 429
     *   When an issue is created
     *   Then the call is attempted "rate-limit.max-attempts" times and then fails with HTTP 429
     */
    @Test
    void whenRateLimitPersistsThenFailsAfterMaxAttempts() {
        when(jiraRestUtils.genericPost(anyString(), any(), any(), any())).thenThrow(new ClientException(429, "slow down"));

        InternalException e = assertThrows(InternalException.class, () -> client.post("/rest/api/2/issue", body));

        assertTrue(e.getMessage().contains("HTTP 429"));
        verify(jiraRestUtils, times(3)).genericPost(anyString(), any(), any(), any());
    }

    /**
     * Scenario: Automation webhook errors never expose the webhook URL
     *   Given the Automation webhook answers 403
     *   When the webhook is called
     *   Then the error message does not contain the webhook URL or token
     */
    @Test
    void whenAutomationWebhookRejectedThenUrlAndTokenNotExposed() {
        when(plainRestUtils.genericPost(contains("automation.example.test"), any(), any(), any()))
                .thenThrow(new ClientException(403, "forbidden"));

        InternalException e = assertThrows(InternalException.class,
                () -> client.postAutomationWebhook("https://automation.example.test/pro/hook-secret", "tok-secret", body));

        assertTrue(e.getMessage().contains("Jira Automation webhook rejected the call (HTTP 403)"));
        assertFalse(e.getMessage().contains("hook-secret"));
        assertFalse(e.getMessage().contains("tok-secret"));
    }

    @Test
    void whenKeyBlankThenBrowseLinkEmpty() {
        assertEquals("", client.browseLink(""));
        assertEquals("https://acme.atlassian.net/browse/ACC-1", client.browseLink("ACC-1"));
    }
}
