package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.OutcomeRulesService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.RequestFilterService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira.TicketTemplateRenderer;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcomeCause;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class JiraConfigValidatorTest {

    private static final String CLOUD_GATEWAY = "https://api.atlassian.com/ex/jira/cloud-1";

    /**
     * Scenario: Templates cannot override the correlation field
     *   Given the subscribe profile renders a field "customfield_10050" with another value
     *   When the configuration is validated
     *   Then validation fails naming the correlation field
     */
    @Test
    void whenFieldOverridesCorrelationThenValidationFails() {
        JiraConfigValidator validator = validator(c -> {}, Map.of("customfield_10050", "forged", "summary", "x"), "ISSUE");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(e.getMessage().contains("1. profile subscribe: field 'customfield_10050' is controlled by the executor (it is the correlation field)"), e.getMessage());
        assertFalse(e.getMessage().contains("secret-token"));
    }

    /**
     * Scenario: REST v3 is rejected on Data Center
     *   Given deployment DATA_CENTER and api-version 3
     *   When the configuration is validated
     *   Then validation fails stating that Data Center supports REST v2 only
     */
    @Test
    void whenApiVersion3OnDataCenterThenValidationFails() {
        JiraConfigValidator validator = validator(c -> {
            when(c.deployment()).thenReturn(JiraDeploymentType.DATA_CENTER);
            when(c.apiVersion()).thenReturn(3);
            when(c.auth().type()).thenReturn(JiraAuthType.PERSONAL_ACCESS_TOKEN);
            when(c.auth().pat().token()).thenReturn(Optional.of("secret-token"));
        }, Map.of("summary", "x"), "ISSUE");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(e.getMessage().contains("Data Center supports REST v2 only"), e.getMessage());
    }

    /**
     * Scenario: Cloud OAuth requires the Atlassian API gateway URL
     *   Given deployment CLOUD, auth type OAUTH2_CLIENT_CREDENTIALS and no api-url
     *   When the configuration is validated
     *   Then validation fails naming "api-url"
     */
    @Test
    void whenCloudOauthWithoutApiUrlThenValidationFails() {
        JiraConfigValidator validator = validator(c -> {
            when(c.auth().type()).thenReturn(JiraAuthType.OAUTH2_CLIENT_CREDENTIALS);
            when(c.auth().oauth2().clientId()).thenReturn(Optional.of("cid"));
            when(c.auth().oauth2().clientSecret()).thenReturn(Optional.of("secret-token"));
            when(c.apiUrl()).thenReturn(Optional.empty());
        }, Map.of("summary", "x"), "ISSUE");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(e.getMessage().contains("api-url"), e.getMessage());
        assertFalse(e.getMessage().contains("secret-token"));
    }

    @Test
    void whenPersonalAccessTokenOnCloudThenValidationFails() {
        JiraConfigValidator validator = validator(c -> {
            when(c.auth().type()).thenReturn(JiraAuthType.PERSONAL_ACCESS_TOKEN);
            when(c.auth().pat().token()).thenReturn(Optional.of("secret-token"));
        }, Map.of("summary", "x"), "ISSUE");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(e.getMessage().contains("PERSONAL_ACCESS_TOKEN is not available on Cloud"), e.getMessage());
    }

    @Test
    void whenCorrelationFieldIsNotCustomThenValidationFails() {
        JiraConfigValidator validator = validator(c -> when(c.correlation().field()).thenReturn("labels"), Map.of("summary", "x"), "ISSUE");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(e.getMessage().contains("jira.correlation.field must be a custom field id"), e.getMessage());
    }

    @Test
    void whenServiceDeskIdsMissingThenValidationFails() {
        JiraConfigValidator validator = validator(c -> {}, Map.of("summary", "x"), "SERVICE_DESK_REQUEST");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(e.getMessage().contains("service-desk-id is required for SERVICE_DESK_REQUEST"), e.getMessage());
        assertTrue(e.getMessage().contains("request-type-id is required for SERVICE_DESK_REQUEST"), e.getMessage());
    }

    @Test
    void whenConfigurationIsValidThenValidationPasses() {
        assertDoesNotThrow(validator(c -> {}, Map.of("summary", "x"), "ISSUE")::validate);
    }

    private JiraConfigValidator validator(Consumer<JiraConfig> customise, Map<String, String> subscribeFields, String subscribeType) {
        JiraConfig config = mock(JiraConfig.class, RETURNS_DEEP_STUBS);
        when(config.deployment()).thenReturn(JiraDeploymentType.CLOUD);
        when(config.siteUrl()).thenReturn("https://acme.atlassian.net");
        when(config.apiUrl()).thenReturn(Optional.of(CLOUD_GATEWAY));
        when(config.apiVersion()).thenReturn(2);
        when(config.auth().type()).thenReturn(JiraAuthType.API_TOKEN);
        when(config.auth().apiToken().email()).thenReturn(Optional.of("bot@acme.com"));
        when(config.auth().apiToken().token()).thenReturn(Optional.of("secret-token"));
        when(config.correlation().field()).thenReturn("customfield_10050");
        customise.accept(config);
        JiraConfig.IssueProfile subscribe = profile(IssueInterfaceType.valueOf(subscribeType), subscribeFields);
        JiraConfig.IssueProfile unsubscribe = profile(IssueInterfaceType.ISSUE, Map.of("summary", "y"));
        when(config.profiles().forOperation(MarketplaceOperation.MARKETPLACE_SUBSCRIBE)).thenReturn(subscribe);
        when(config.profiles().forOperation(MarketplaceOperation.MARKETPLACE_UNSUBSCRIBE)).thenReturn(unsubscribe);
        return new JiraConfigValidator(config, mock(RequestFilterService.class), mock(OutcomeRulesService.class), mock(TicketTemplateRenderer.class));
    }

    private JiraConfig.IssueProfile profile(IssueInterfaceType type, Map<String, String> fields) {
        List<JiraConfig.OutcomeRule> rules = List.of(fulfilledRule());
        JiraConfig.IssueProfile profile = mock(JiraConfig.IssueProfile.class, RETURNS_DEEP_STUBS);
        when(profile.type()).thenReturn(type);
        when(profile.project()).thenReturn(Optional.of("ACC"));
        when(profile.issueType()).thenReturn(Optional.of("Task"));
        when(profile.serviceDeskId()).thenReturn(Optional.empty());
        when(profile.requestTypeId()).thenReturn(Optional.empty());
        when(profile.webhookUrl()).thenReturn(Optional.empty());
        when(profile.webhookToken()).thenReturn(Optional.empty());
        when(profile.setReporter()).thenReturn(false);
        when(profile.fields()).thenReturn(fields);
        when(profile.objectFields()).thenReturn(Map.of());
        when(profile.listFields()).thenReturn(Map.of());
        when(profile.outcomeRules()).thenReturn(rules);
        return profile;
    }

    private JiraConfig.OutcomeRule fulfilledRule() {
        JiraConfig.OutcomeRule rule = mock(JiraConfig.OutcomeRule.class);
        when(rule.when()).thenReturn(Map.of("statusCategory", "done"));
        when(rule.outcome()).thenReturn(TicketOutcomeCause.FULFILLED);
        when(rule.message()).thenReturn(Optional.empty());
        return rule;
    }
}
