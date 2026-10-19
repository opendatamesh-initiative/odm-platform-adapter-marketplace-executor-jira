package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class JiraRestUtilsProducerTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> tokenRequestBody = new AtomicReference<>();
    private final AtomicReference<String> apiAuthorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/oauth/token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, "{\"access_token\":\"tok-123\",\"expires_in\":3600}");
        });
        server.createContext("/rest/api/2/serverInfo", exchange -> {
            apiAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, "{}");
        });
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private JiraConfig config(JiraDeploymentType deployment, JiraAuthType type) {
        JiraConfig config = mock(JiraConfig.class, RETURNS_DEEP_STUBS);
        when(config.deployment()).thenReturn(deployment);
        when(config.siteUrl()).thenReturn(base);
        when(config.auth().type()).thenReturn(type);
        return config;
    }

    private void callJira(JiraConfig config) {
        RestUtils restUtils = new JiraRestUtilsProducer().jiraRestUtils(config, new ObjectMapper());
        restUtils.genericGet(base + "/rest/api/2/serverInfo", List.of(), null, JsonNode.class);
    }

    /**
     * Scenario: Service account client credentials authenticate Jira Cloud calls
     *   Given the executor is configured with auth type OAUTH2_CLIENT_CREDENTIALS on Jira Cloud
     *   When the Jira client is produced
     *   Then it requests a token with grant type "client_credentials" from the configured token URL
     *   And it sends Jira calls with a Bearer token
     */
    @Test
    void whenOauthClientCredentialsThenBearerTokenRequested() {
        JiraConfig config = config(JiraDeploymentType.CLOUD, JiraAuthType.OAUTH2_CLIENT_CREDENTIALS);
        when(config.auth().oauth2().clientId()).thenReturn(Optional.of("cid"));
        when(config.auth().oauth2().clientSecret()).thenReturn(Optional.of("csecret"));
        when(config.auth().oauth2().tokenUrl()).thenReturn(Optional.of(base + "/oauth/token"));
        when(config.auth().oauth2().scope()).thenReturn(Optional.empty());

        callJira(config);

        assertTrue(tokenRequestBody.get().contains("grant_type=client_credentials"));
        assertTrue(tokenRequestBody.get().contains("client_id=cid"));
        assertFalse(tokenRequestBody.get().contains("scope"));
        assertEquals("Bearer tok-123", apiAuthorization.get());
    }

    @Test
    void whenApiTokenThenBasicHeaderSent() {
        JiraConfig config = config(JiraDeploymentType.CLOUD, JiraAuthType.API_TOKEN);
        when(config.auth().apiToken().email()).thenReturn(Optional.of("bot@acme.com"));
        when(config.auth().apiToken().token()).thenReturn(Optional.of("api-token"));

        callJira(config);

        String expected = Base64.getEncoder().encodeToString("bot@acme.com:api-token".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expected, apiAuthorization.get());
    }

    /**
     * Scenario: Personal access token authenticates Jira Data Center calls
     *   Given the executor is configured with auth type PERSONAL_ACCESS_TOKEN on Jira Data Center
     *   When the Jira client is produced
     *   Then it sends Jira calls with "Authorization: Bearer" and the token
     */
    @Test
    void whenPersonalAccessTokenThenBearerHeaderSent() {
        JiraConfig config = config(JiraDeploymentType.DATA_CENTER, JiraAuthType.PERSONAL_ACCESS_TOKEN);
        when(config.auth().pat().token()).thenReturn(Optional.of("pat-123"));

        callJira(config);

        assertEquals("Bearer pat-123", apiAuthorization.get());
        assertEquals(null, tokenRequestBody.get());
    }

    private void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
