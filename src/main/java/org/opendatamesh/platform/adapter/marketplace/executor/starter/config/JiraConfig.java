package org.opendatamesh.platform.adapter.marketplace.executor.starter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcomeCause;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "jira")
public interface JiraConfig {

    JiraDeploymentType deployment();

    /** Used for browse links and, on Data Center, as the API base. */
    String siteUrl();

    /** API base; required on Cloud with OAuth, e.g. {@code https://api.atlassian.com/ex/jira/<cloudId>}. Defaults to {@code siteUrl}. */
    Optional<String> apiUrl();

    /** 2 or 3 (3 on Cloud only). */
    @WithDefault("2")
    int apiVersion();

    default String apiBaseUrl() {
        return stripTrailingSlash(apiUrl().orElse(siteUrl()));
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    Auth auth();

    Correlation correlation();

    Webhook webhook();

    RateLimit rateLimit();

    Filter filter();

    Profiles profiles();

    Messages messages();

    interface Auth {

        @WithDefault("OAUTH2_CLIENT_CREDENTIALS")
        JiraAuthType type();

        OAuth2 oauth2();

        ApiToken apiToken();

        Pat pat();
    }

    interface OAuth2 {

        Optional<String> clientId();

        Optional<String> clientSecret();

        /** Defaults to the Atlassian token endpoint on Cloud and {@code siteUrl + "/rest/oauth2/latest/token"} on Data Center. */
        Optional<String> tokenUrl();

        Optional<String> scope();
    }

    interface ApiToken {

        Optional<String> email();

        Optional<String> token();
    }

    interface Pat {

        Optional<String> token();
    }

    interface Correlation {

        /** Jira text custom field that stores the marketplace request UUID, e.g. {@code customfield_10050}. */
        String field();

        /** Also add the label {@code bdmp-<uuid>} to the issue. */
        @WithDefault("false")
        boolean label();
    }

    interface Webhook {

        /** Extra normalised event field name to JSON Pointer into the native webhook payload. */
        Map<String, String> extraFields();
    }

    interface RateLimit {

        @WithDefault("3")
        int maxAttempts();

        @WithDefault("2s")
        Duration backoff();
    }

    /** Every criterion is a list of full-match regular expressions; an absent criterion matches everything. */
    interface Filter {

        Optional<List<String>> operations();

        Optional<List<String>> providerDataProductFqns();

        Optional<List<String>> portFqns();

        Optional<List<String>> consumerTypes();

        Optional<List<String>> consumerIdentifiers();

        Optional<List<String>> requesterIdentifiers();

        /** Request property key to regular expression. */
        Map<String, String> properties();
    }

    interface Profiles {

        IssueProfile subscribe();

        IssueProfile unsubscribe();

        default IssueProfile forOperation(MarketplaceOperation operation) {
            return switch (operation) {
                case MARKETPLACE_SUBSCRIBE -> subscribe();
                case MARKETPLACE_UNSUBSCRIBE -> unsubscribe();
            };
        }
    }

    interface IssueProfile {

        IssueInterfaceType type();

        Optional<String> project();

        Optional<String> issueType();

        Optional<String> serviceDeskId();

        Optional<String> requestTypeId();

        Optional<String> webhookUrl();

        Optional<String> webhookToken();

        /** SERVICE_DESK_REQUEST only: set the correlation field with a follow-up edit instead of at creation. */
        @WithDefault("false")
        boolean correlationViaEdit();

        /** ISSUE only: set the reporter to the marketplace requester. */
        @WithDefault("false")
        boolean setReporter();

        Map<String, String> fields();

        Map<String, Map<String, String>> objectFields();

        Map<String, List<String>> listFields();

        /** Fields whose rendered value is converted to ADF when {@code apiVersion} is 3. */
        @WithDefault("description")
        List<String> richTextFields();

        List<OutcomeRule> outcomeRules();

        ProfileMessages messages();
    }

    interface OutcomeRule {

        /** Issue field to full-match regular expression. */
        Map<String, String> when();

        TicketOutcomeCause outcome();

        Optional<String> message();
    }

    interface ProfileMessages {

        Optional<String> pending();

        Optional<String> fulfilled();

        Optional<String> refused();

        Optional<String> failed();

        Optional<String> technicalError();
    }

    interface Messages {

        @WithDefault("{#if ticket.key}Jira issue {ticket.key} opened{#else}Request submitted to the Jira Automation rule{/if} for {operation} of {request.provider.dataProductFqn} (ports: {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}). Awaiting fulfilment{#if ticket.link}: {ticket.link}{/if}")
        String pending();

        @WithDefault("{outcome.status} through Jira issue {ticket.key} ({ticket.link}){#if ticket.closedBy} by {ticket.closedBy}{/if}: {fields.status}{#if fields.resolution} / {fields.resolution}{/if}.{#if fields.lastComment} Comment: {fields.lastComment}{/if}")
        String fulfilled();

        @WithDefault("Request refused on Jira: issue {ticket.key} ({ticket.link}){#if ticket.closedBy} by {ticket.closedBy}{/if}, {fields.status}{#if fields.resolution} / {fields.resolution}{/if}.{#if fields.lastComment} Reason: {fields.lastComment}{/if}")
        String refused();

        @WithDefault("Jira issue {ticket.key} ({ticket.link}) was closed without a recognised resolution ({fields.status}{#if fields.resolution} / {fields.resolution}{/if}).{#if fields.lastComment} Details: {fields.lastComment}{/if}")
        String failed();

        @WithDefault("Technical error while processing {operation} on Jira: {error.message}")
        String technicalError();

        ClosingUser closingUser();
    }

    interface ClosingUser {

        @WithDefault("true")
        boolean include();

        @WithDefault("actor")
        String field();
    }
}
