package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.BadRequestException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest.ProcessMarketplaceRequestCommand;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest.ProcessMarketplaceRequestFactory;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.processrequest.ConsumerRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.processrequest.MarketplaceRequestRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.ProviderRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.processrequest.RequestRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.processrequest.RequesterRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEvent;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEventIgnoredReason;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket.CompleteTicketCommand;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket.CompleteTicketFactory;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket.CompleteTicketPresenter;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.completeticket.TicketEventCommandRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.usecases.completeticket.TicketEventResultRes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class MarketplaceExecutorUseCasesService {

    private final ProcessMarketplaceRequestFactory processMarketplaceRequestFactory;
    private final CompleteTicketFactory completeTicketFactory;
    private final ObjectMapper objectMapper;
    private final JiraWebhookEventMapper jiraWebhookEventMapper;

    public MarketplaceExecutorUseCasesService(
            ProcessMarketplaceRequestFactory processMarketplaceRequestFactory,
            CompleteTicketFactory completeTicketFactory,
            ObjectMapper objectMapper,
            JiraWebhookEventMapper jiraWebhookEventMapper
    ) {
        this.processMarketplaceRequestFactory = processMarketplaceRequestFactory;
        this.completeTicketFactory = completeTicketFactory;
        this.objectMapper = objectMapper;
        this.jiraWebhookEventMapper = jiraWebhookEventMapper;
    }

    public TicketEventResultRes processTicketEvent(TicketEventCommandRes res) {
        return run(toTicketEvent(res));
    }

    public TicketEventResultRes processJiraWebhookEvent(ObjectNode payload) {
        return run(jiraWebhookEventMapper.toEvent(payload));
    }

    private TicketEventResultRes run(TicketEvent event) {
        CompleteTicketCommand command = new CompleteTicketCommand(event);
        TicketEventResultHolder holder = new TicketEventResultHolder();
        completeTicketFactory.buildCompleteTicket(command, holder).execute();
        return holder.toRes();
    }

    private TicketEvent toTicketEvent(TicketEventCommandRes res) {
        if (res == null) {
            return new TicketEvent(null, Map.of());
        }
        return new TicketEvent(trimToNull(res.getCorrelationId()), stringify(res.getFields()));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, String> stringify(Map<String, Object> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (value != null) {
                    result.put(key, asText(value));
                }
            });
        }
        return result;
    }

    private String asText(Object value) {
        if (value instanceof Map || value instanceof Iterable) {
            return toJson(value);
        }
        return String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    public void processRequest(MarketplaceRequestRes requestRes) {
        ProcessMarketplaceRequestCommand command = toCommand(requestRes);
        processMarketplaceRequestFactory.buildProcessMarketplaceRequest(command).execute();
    }

    private ProcessMarketplaceRequestCommand toCommand(MarketplaceRequestRes requestRes) {
        return new ProcessMarketplaceRequestCommand(toOperation(requestRes.getOperation()), toRequest(requestRes.getRequest()));
    }

    private MarketplaceOperation toOperation(String operation) {
        try {
            return MarketplaceOperation.valueOf(operation);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid operation type. Must be either MARKETPLACE_SUBSCRIBE or MARKETPLACE_UNSUBSCRIBE");
        }
    }

    private MarketplaceRequest toRequest(RequestRes res) {
        if (res == null) {
            return null;
        }
        return new MarketplaceRequest(
                res.getName(),
                res.getIdentifier(),
                toProvider(res.getProvider()),
                toParty(res.getConsumer()),
                toParty(res.getRequester()),
                res.getProperties(),
                res.getStartDate(),
                res.getEndDate()
        );
    }

    private Provider toProvider(ProviderRes res) {
        return res == null ? null : new Provider(res.getDataProductFqn(), res.getDataProductPortsFqn());
    }

    private Party toParty(ConsumerRes res) {
        return res == null ? null : new Party(res.getType(), res.getIdentifier());
    }

    private Party toParty(RequesterRes res) {
        return res == null ? null : new Party(res.getType(), res.getIdentifier());
    }

    private static final class TicketEventResultHolder implements CompleteTicketPresenter {

        private MarketplaceRequestStatus status;
        private TicketEventIgnoredReason reason;

        @Override
        public void presentReported(MarketplaceRequestStatus status) {
            this.status = status;
        }

        @Override
        public void presentIgnored(TicketEventIgnoredReason reason) {
            this.reason = reason;
        }

        TicketEventResultRes toRes() {
            TicketEventResultRes res = new TicketEventResultRes();
            res.setOutcome(reason == null ? "REPORTED" : "IGNORED");
            res.setStatus(status);
            res.setReason(reason);
            return res;
        }
    }
}
