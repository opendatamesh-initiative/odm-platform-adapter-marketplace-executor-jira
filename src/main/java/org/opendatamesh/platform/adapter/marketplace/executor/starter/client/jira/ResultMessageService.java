package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@ApplicationScoped
public class ResultMessageService {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final JiraConfig config;
    private final TicketTemplateRenderer renderer;
    private final JiraClient client;

    public ResultMessageService(JiraConfig config, TicketTemplateRenderer renderer, JiraClient client) {
        this.config = config;
        this.renderer = renderer;
        this.client = client;
    }

    public String pending(MarketplaceOperation operation, MarketplaceRequest request, IssueReference issue) {
        Map<String, Object> data = renderer.requestData(operation, request);
        data.put("ticket", ticketData(issue.key(), issue.id(), issue.link(), null));
        String template = chooseTemplate(operation, null, JiraConfig.ProfileMessages::pending, JiraConfig.Messages::pending);
        return finish(renderer.render(template, data));
    }

    public String finalOutcome(MarketplaceOperation operation, MarketplaceRequest request, TicketOutcome outcome,
                               MarketplaceRequestStatus status, Map<String, String> fields) {
        Map<String, Object> data = renderer.requestData(operation, request);
        data.put("ticket", ticketDataFromFields(fields));
        data.put("fields", exposedFields(fields));
        data.put("outcome", Map.of("cause", outcome.cause().name(), "status", status.name()));
        String template = chooseTemplate(operation, outcome.messageTemplate(), causeProfileMessage(outcome), causeGlobalMessage(outcome));
        return finish(renderer.render(template, data));
    }

    public String technicalError(MarketplaceOperation operation, MarketplaceRequest request, String errorMessage) {
        Map<String, Object> data = renderer.requestData(operation, request);
        data.put("error", Map.of("message", errorMessage == null ? "" : errorMessage));
        String template = chooseTemplate(operation, null, JiraConfig.ProfileMessages::technicalError, JiraConfig.Messages::technicalError);
        return finish(renderer.render(template, data));
    }

    private String chooseTemplate(MarketplaceOperation operation, String ruleMessage,
                                  Function<JiraConfig.ProfileMessages, Optional<String>> profileMessage,
                                  Function<JiraConfig.Messages, String> globalMessage) {
        if (ruleMessage != null) {
            return ruleMessage;
        }
        return profileMessage.apply(config.profiles().forOperation(operation).messages())
                .orElseGet(() -> globalMessage.apply(config.messages()));
    }

    private Function<JiraConfig.ProfileMessages, Optional<String>> causeProfileMessage(TicketOutcome outcome) {
        return switch (outcome.cause()) {
            case FULFILLED -> JiraConfig.ProfileMessages::fulfilled;
            case REFUSED -> JiraConfig.ProfileMessages::refused;
            case FAILED -> JiraConfig.ProfileMessages::failed;
        };
    }

    private Function<JiraConfig.Messages, String> causeGlobalMessage(TicketOutcome outcome) {
        return switch (outcome.cause()) {
            case FULFILLED -> JiraConfig.Messages::fulfilled;
            case REFUSED -> JiraConfig.Messages::refused;
            case FAILED -> JiraConfig.Messages::failed;
        };
    }

    private Map<String, Object> ticketDataFromFields(Map<String, String> fields) {
        String key = fields.get("key");
        return ticketData(key, fields.get("id"), client.browseLink(key), fields.get(closingUserField()));
    }

    private Map<String, Object> ticketData(String key, String id, String link, String closedBy) {
        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("key", orEmpty(key));
        ticket.put("id", orEmpty(id));
        ticket.put("link", orEmpty(link));
        if (closingUserIncluded() && closedBy != null) {
            ticket.put("closedBy", closedBy);
        }
        return ticket;
    }

    private Map<String, String> exposedFields(Map<String, String> fields) {
        Map<String, String> exposed = new LinkedHashMap<>(fields);
        if (!closingUserIncluded()) {
            exposed.remove(closingUserField());
        }
        return exposed;
    }

    private boolean closingUserIncluded() {
        return config.messages().closingUser().include();
    }

    private String closingUserField() {
        return config.messages().closingUser().field();
    }

    private String finish(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= MAX_MESSAGE_LENGTH ? trimmed : trimmed.substring(0, MAX_MESSAGE_LENGTH);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
