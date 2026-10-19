package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.MarketplaceServiceConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.RegisteredAccessRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.MarketplaceResponseRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.ProviderRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class MarketplaceServiceClient {

    private static final Logger log = Logger.getLogger(MarketplaceServiceClient.class);
    private static final String REQUEST_PATH = "/api/v1/pp/marketplace/requests/%s";
    private static final String RESULTS_PATH = "/api/v1/pp/marketplace/requests/%s/results";
    private static final String RESPONSES_PATH = "/api/v1/pp/marketplace/executor-responses?accessRequestUuid=%s&page=0&size=100";

    private final RestUtils restUtils;
    private final MarketplaceServiceConfig config;
    private final ObjectMapper objectMapper;

    public MarketplaceServiceClient(RestUtils restUtils, MarketplaceServiceConfig config, ObjectMapper objectMapper) {
        this.restUtils = restUtils;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public Optional<RegisteredAccessRequest> findAccessRequest(String uuid) {
        try {
            JsonNode node = restUtils.genericGet(config.address() + REQUEST_PATH.formatted(uuid), jsonHeaders(), null, JsonNode.class);
            return Optional.of(toRegisteredAccessRequest(node));
        } catch (ClientException e) {
            if (e.getCode() == 404) {
                return Optional.empty();
            }
            throw lookupFailed(uuid, e);
        } catch (RuntimeException e) {
            throw lookupFailed(uuid, e);
        }
    }

    public boolean hasFinalResult(String uuid) {
        try {
            String url = config.address() + RESPONSES_PATH.formatted(URLEncoder.encode(uuid, StandardCharsets.UTF_8));
            JsonNode page = restUtils.genericGet(url, jsonHeaders(), null, JsonNode.class);
            return containsFinalStatus(page.path("content"));
        } catch (RuntimeException e) {
            throw new InternalException("Marketplace Service results lookup failed for request " + uuid, e);
        }
    }

    public void sendResult(String uuid, MarketplaceResult result) {
        int maxAttempts = Math.max(1, config.resultDelivery().maxAttempts());
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                postResult(uuid, result);
                return;
            } catch (RuntimeException e) {
                if (!isRetryable(e)) {
                    throw deliveryFailed(uuid, result, e);
                }
                lastError = e;
                log.warnf("Result delivery attempt %d/%d failed for request %s: %s", attempt, maxAttempts, uuid, e.getMessage());
                if (attempt < maxAttempts) {
                    pauseBeforeRetry();
                }
            }
        }
        throw deliveryFailed(uuid, result, lastError);
    }

    private void postResult(String uuid, MarketplaceResult result) {
        restUtils.genericPost(config.address() + RESULTS_PATH.formatted(uuid), jsonHeaders(),
                toResponseRes(result), Void.class);
    }

    private boolean isRetryable(RuntimeException e) {
        if (e instanceof ClientException clientException) {
            return clientException.getCode() == 404 || clientException.getCode() >= 500;
        }
        return hasIoCause(e);
    }

    private boolean hasIoCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(config.resultDelivery().backoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("Interrupted while waiting to retry result delivery", e);
        }
    }

    private InternalException deliveryFailed(String uuid, MarketplaceResult result, RuntimeException lastError) {
        return new InternalException("Unable to deliver result " + result.status() + " for request " + uuid + ": "
                + (lastError == null ? "unknown error" : lastError.getMessage()), lastError);
    }

    private InternalException lookupFailed(String uuid, RuntimeException cause) {
        return new InternalException("Marketplace Service lookup failed for request " + uuid, cause);
    }

    private boolean containsFinalStatus(JsonNode content) {
        for (JsonNode response : content) {
            if (!"PENDING".equals(response.path("status").asText("PENDING"))) {
                return true;
            }
        }
        return false;
    }

    private RegisteredAccessRequest toRegisteredAccessRequest(JsonNode node) {
        MarketplaceOperation operation = MarketplaceOperation.valueOf(node.path("operation").asText());
        MarketplaceRequest request = new MarketplaceRequest(
                text(node, "name"),
                text(node, "uuid"),
                new Provider(text(node, "providerDataProductFqn"), ports(node.path("providerDataProductPortsFqn"))),
                new Party(text(node, "consumerType"), text(node, "consumerIdentifier")),
                new Party(text(node, "requesterType"), text(node, "requesterIdentifier")),
                properties(node.path("properties")),
                date(node.path("startDate")),
                date(node.path("endDate"))
        );
        return new RegisteredAccessRequest(operation, request);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private List<String> ports(JsonNode node) {
        List<String> ports = new ArrayList<>();
        node.forEach(port -> ports.add(port.asText()));
        return ports;
    }

    private Map<String, Object> properties(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private Date date(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : objectMapper.convertValue(node, Date.class);
    }

    private List<HttpHeader> jsonHeaders() {
        return List.of(new HttpHeader(HttpHeader.CONTENT_TYPE, "application/json"));
    }

    private MarketplaceResponseRes toResponseRes(MarketplaceResult result) {
        MarketplaceResponseRes response = new MarketplaceResponseRes();
        response.setStatus(result.status());
        response.setMessage(result.message());
        response.setProvider(toProviderRes(result.provider()));
        response.setCreatedAt(result.createdAt());
        return response;
    }

    private ProviderRes toProviderRes(Provider provider) {
        if (provider == null) {
            return null;
        }
        ProviderRes res = new ProviderRes();
        res.setDataProductFqn(provider.dataProductFqn());
        res.setDataProductPortsFqn(provider.dataProductPortsFqn());
        return res;
    }
}
