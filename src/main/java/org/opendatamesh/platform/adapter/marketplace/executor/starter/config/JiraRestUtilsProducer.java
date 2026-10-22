package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtilsFactory;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.Oauth2;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

public class JiraRestUtilsProducer {

    private static final String ATLASSIAN_TOKEN_URL = "https://auth.atlassian.com/oauth/token";

    @Produces
    @ApplicationScoped
    @JiraApi
    RestUtils jiraRestUtils(JiraConfig config, ObjectMapper objectMapper) {
        return RestUtilsFactory.getAuthenticatedRestUtils(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build(),
                objectMapper,
                connectionHeaders(config),
                oauth2(config)
        );
    }

    private List<HttpHeader> connectionHeaders(JiraConfig config) {
        return switch (config.auth().type()) {
            case OAUTH2_CLIENT_CREDENTIALS -> List.of();
            case API_TOKEN -> List.of(new HttpHeader("Authorization", "Basic " + basicCredentials(config.auth().apiToken())));
            case PERSONAL_ACCESS_TOKEN -> List.of(HttpHeader.bearerAuth(config.auth().pat().token().orElseThrow()));
        };
    }

    private Oauth2 oauth2(JiraConfig config) {
        if (config.auth().type() != JiraAuthType.OAUTH2_CLIENT_CREDENTIALS) {
            return null;
        }
        JiraConfig.OAuth2 settings = config.auth().oauth2();
        Oauth2 oauth2 = new Oauth2();
        oauth2.setUrl(tokenUrl(config));
        oauth2.setGrantType("client_credentials");
        oauth2.setScope(settings.scope().orElse(null));
        oauth2.setClientId(settings.clientId().orElse(null));
        oauth2.setClientSecret(settings.clientSecret().orElse(null));
        return oauth2;
    }

    private String tokenUrl(JiraConfig config) {
        return config.auth().oauth2().tokenUrl().orElse(switch (config.deployment()) {
            case CLOUD -> ATLASSIAN_TOKEN_URL;
            case DATA_CENTER -> stripTrailingSlash(config.siteUrl()) + "/rest/oauth2/latest/token";
        });
    }

    private String basicCredentials(JiraConfig.ApiToken apiToken) {
        String credentials = apiToken.email().orElseThrow() + ":" + apiToken.token().orElseThrow();
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
