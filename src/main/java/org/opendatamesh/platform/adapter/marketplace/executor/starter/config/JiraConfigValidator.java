package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcomeCause;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core.OutcomeRulesService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core.RequestFilterService;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core.TicketTemplateRenderer;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class JiraConfigValidator {

    private static final String CLOUD_API_GATEWAY = "https://api.atlassian.com/ex/jira/";
    private static final String CORRELATION_FIELD_PATTERN = "customfield_\\d+";

    private final JiraConfig config;
    private final RequestFilterService filterService;
    private final OutcomeRulesService outcomeRulesService;
    private final TicketTemplateRenderer renderer;

    public JiraConfigValidator(JiraConfig config, RequestFilterService filterService,
                                     OutcomeRulesService outcomeRulesService, TicketTemplateRenderer renderer) {
        this.config = config;
        this.filterService = filterService;
        this.outcomeRulesService = outcomeRulesService;
        this.renderer = renderer;
    }

    void onStart(@Observes StartupEvent event) {
        validate();
    }

    public void validate() {
        List<String> problems = new ArrayList<>();
        verifyConnection(problems);
        verifyCorrelation(problems);
        for (MarketplaceOperation operation : MarketplaceOperation.values()) {
            JiraConfig.IssueProfile profile = config.profiles().forOperation(operation);
            verifyInterfaceSettings(problems, operation, profile);
            verifyFields(problems, operation, profile);
            verifyRules(problems, operation, profile);
        }
        verifyRulePatterns(problems);
        verifyFilter(problems);
        verifyTemplates(problems);
        failIfAny(problems);
    }

    private void verifyConnection(List<String> problems) {
        verifyUrl(problems, "jira.site-url", config.siteUrl());
        config.apiUrl().ifPresent(url -> verifyUrl(problems, "jira.api-url", url));
        verifyVersionAndDeployment(problems);
        verifyAuth(problems);
    }

    private void verifyUrl(List<String> problems, String name, String url) {
        if (isBlank(url)) {
            problems.add(name + " is required");
        } else if (!isValidUrl(url)) {
            problems.add(name + " must be a valid https URL (http is allowed only for http://localhost)");
        }
    }

    private boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean localhost = url.startsWith("http://localhost");
            return (secure || localhost) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void verifyVersionAndDeployment(List<String> problems) {
        int version = config.apiVersion();
        if (version != 2 && version != 3) {
            problems.add("jira.api-version must be 2 or 3 but was " + version);
        }
        if (config.deployment() == JiraDeploymentType.DATA_CENTER && version == 3) {
            problems.add("jira.api-version 3 is not available: Data Center supports REST v2 only");
        }
        if (config.deployment() == JiraDeploymentType.CLOUD && config.auth().type() == JiraAuthType.PERSONAL_ACCESS_TOKEN) {
            problems.add("jira.auth.type PERSONAL_ACCESS_TOKEN is not available on Cloud: use OAUTH2_CLIENT_CREDENTIALS or API_TOKEN");
        }
    }

    private void verifyAuth(List<String> problems) {
        JiraConfig.Auth auth = config.auth();
        switch (auth.type()) {
            case OAUTH2_CLIENT_CREDENTIALS -> verifyOauth(problems, auth.oauth2());
            case API_TOKEN -> {
                requireText(problems, auth.apiToken().email(), "jira.auth.api-token.email is required for API_TOKEN");
                requireText(problems, auth.apiToken().token(), "jira.auth.api-token.token is required for API_TOKEN");
            }
            case PERSONAL_ACCESS_TOKEN ->
                    requireText(problems, auth.pat().token(), "jira.auth.pat.token is required for PERSONAL_ACCESS_TOKEN");
        }
    }

    private void verifyOauth(List<String> problems, JiraConfig.OAuth2 oauth2) {
        requireText(problems, oauth2.clientId(), "jira.auth.oauth2.client-id is required for OAUTH2_CLIENT_CREDENTIALS");
        requireText(problems, oauth2.clientSecret(), "jira.auth.oauth2.client-secret is required for OAUTH2_CLIENT_CREDENTIALS");
        boolean gatewayUrl = config.apiUrl().map(url -> url.startsWith(CLOUD_API_GATEWAY)).orElse(false);
        if (config.deployment() == JiraDeploymentType.CLOUD && !gatewayUrl) {
            problems.add("jira.api-url must start with " + CLOUD_API_GATEWAY + "<cloudId> for OAUTH2_CLIENT_CREDENTIALS on Cloud");
        }
    }

    private void verifyCorrelation(List<String> problems) {
        if (!config.correlation().field().matches(CORRELATION_FIELD_PATTERN)) {
            problems.add("jira.correlation.field must be a custom field id like customfield_10050 but was '"
                    + config.correlation().field() + "'");
        }
    }

    private void verifyInterfaceSettings(List<String> problems, MarketplaceOperation operation, JiraConfig.IssueProfile profile) {
        String name = profileName(operation);
        switch (profile.type()) {
            case ISSUE -> {
                requireText(problems, profile.project(), name + ": project is required for ISSUE");
                requireText(problems, profile.issueType(), name + ": issue-type is required for ISSUE");
            }
            case SERVICE_DESK_REQUEST -> {
                requireText(problems, profile.serviceDeskId(), name + ": service-desk-id is required for SERVICE_DESK_REQUEST");
                requireText(problems, profile.requestTypeId(), name + ": request-type-id is required for SERVICE_DESK_REQUEST");
            }
            case AUTOMATION_WEBHOOK -> {
                requireText(problems, profile.webhookUrl(), name + ": webhook-url is required for AUTOMATION_WEBHOOK");
                requireText(problems, profile.webhookToken(), name + ": webhook-token is required for AUTOMATION_WEBHOOK");
                profile.webhookUrl().filter(url -> !isValidUrl(url))
                        .ifPresent(url -> problems.add(name + ": webhook-url must be a valid https URL"));
            }
        }
    }

    private void verifyFields(List<String> problems, MarketplaceOperation operation, JiraConfig.IssueProfile profile) {
        for (String key : reservedKeys(profile)) {
            if (declaredKeys(profile).contains(key)) {
                problems.add(profileName(operation) + ": field '" + key + "' is controlled by the executor"
                        + (key.equals(config.correlation().field()) ? " (it is the correlation field)" : "")
                        + " and must be removed from fields, object-fields and list-fields");
            }
        }
    }

    private Set<String> reservedKeys(JiraConfig.IssueProfile profile) {
        Set<String> reserved = new LinkedHashSet<>(List.of(config.correlation().field(), "project", "issuetype"));
        if (profile.setReporter()) {
            reserved.add("reporter");
        }
        if (profile.type() == IssueInterfaceType.AUTOMATION_WEBHOOK) {
            reserved.add("correlationId");
        }
        return reserved;
    }

    private Set<String> declaredKeys(JiraConfig.IssueProfile profile) {
        Set<String> keys = new LinkedHashSet<>(profile.fields().keySet());
        keys.addAll(profile.objectFields().keySet());
        keys.addAll(profile.listFields().keySet());
        return keys;
    }

    private void verifyRules(List<String> problems, MarketplaceOperation operation, JiraConfig.IssueProfile profile) {
        List<JiraConfig.OutcomeRule> rules = profile.outcomeRules();
        String name = profileName(operation);
        if (rules.isEmpty()) {
            problems.add(name + ": at least one outcome rule is required");
            return;
        }
        if (rules.stream().noneMatch(rule -> rule.outcome() == TicketOutcomeCause.FULFILLED)) {
            problems.add(name + ": at least one outcome rule must have outcome FULFILLED");
        }
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).when().isEmpty()) {
                problems.add(name + ": outcome rule " + i + " must declare a non-empty 'when'");
            }
        }
    }

    private void verifyRulePatterns(List<String> problems) {
        collect(problems, outcomeRulesService::verifyRules);
    }

    private void verifyFilter(List<String> problems) {
        collect(problems, filterService::verifyPatterns);
    }

    private void verifyTemplates(List<String> problems) {
        for (MarketplaceOperation operation : MarketplaceOperation.values()) {
            JiraConfig.IssueProfile profile = config.profiles().forOperation(operation);
            profile.fields().forEach((field, template) -> verifyTemplate(problems, profileName(operation) + " field '" + field + "'", template));
            profile.objectFields().forEach((field, entries) -> entries.forEach((entry, template) ->
                    verifyTemplate(problems, profileName(operation) + " object-field '" + field + "." + entry + "'", template)));
            profile.listFields().forEach((field, items) -> items.forEach(template ->
                    verifyTemplate(problems, profileName(operation) + " list-field '" + field + "'", template)));
            profile.outcomeRules().forEach(rule -> rule.message()
                    .ifPresent(template -> verifyTemplate(problems, profileName(operation) + " outcome rule message", template)));
            verifyProfileMessages(problems, operation, profile.messages());
        }
        verifyGlobalMessages(problems, config.messages());
    }

    private void verifyProfileMessages(List<String> problems, MarketplaceOperation operation, JiraConfig.ProfileMessages messages) {
        String name = profileName(operation) + " message ";
        verifyOptionalTemplate(problems, name + "pending", messages.pending());
        verifyOptionalTemplate(problems, name + "fulfilled", messages.fulfilled());
        verifyOptionalTemplate(problems, name + "refused", messages.refused());
        verifyOptionalTemplate(problems, name + "failed", messages.failed());
        verifyOptionalTemplate(problems, name + "technical-error", messages.technicalError());
    }

    private void verifyGlobalMessages(List<String> problems, JiraConfig.Messages messages) {
        verifyTemplate(problems, "global message pending", messages.pending());
        verifyTemplate(problems, "global message fulfilled", messages.fulfilled());
        verifyTemplate(problems, "global message refused", messages.refused());
        verifyTemplate(problems, "global message failed", messages.failed());
        verifyTemplate(problems, "global message technical-error", messages.technicalError());
    }

    private void verifyOptionalTemplate(List<String> problems, String name, Optional<String> template) {
        template.ifPresent(value -> verifyTemplate(problems, name, value));
    }

    private void verifyTemplate(List<String> problems, String name, String template) {
        if (template == null) {
            return;
        }
        try {
            renderer.verify(template);
        } catch (RuntimeException e) {
            problems.add(name + ": " + e.getMessage());
        }
    }

    private void collect(List<String> problems, Runnable verification) {
        try {
            verification.run();
        } catch (RuntimeException e) {
            problems.add(e.getMessage());
        }
    }

    private void requireText(List<String> problems, Optional<String> value, String message) {
        if (value.isEmpty() || value.get().isBlank()) {
            problems.add(message);
        }
    }

    private void failIfAny(List<String> problems) {
        if (problems.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder("Invalid Jira executor configuration:");
        for (int i = 0; i < problems.size(); i++) {
            message.append("\n  ").append(i + 1).append(". ").append(problems.get(i));
        }
        throw new IllegalStateException(message.toString());
    }

    private String profileName(MarketplaceOperation operation) {
        return "profile " + (operation == MarketplaceOperation.MARKETPLACE_SUBSCRIBE ? "subscribe" : "unsubscribe");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
