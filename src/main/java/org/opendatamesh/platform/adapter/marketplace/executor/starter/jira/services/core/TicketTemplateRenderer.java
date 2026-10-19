package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateException;
import jakarta.enterprise.context.ApplicationScoped;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class TicketTemplateRenderer {

    private static final String FILE_PREFIX = "file:";
    private static final int EXCERPT_LENGTH = 80;

    private final Engine engine;
    private final ConcurrentMap<String, Template> cache = new ConcurrentHashMap<>();

    public TicketTemplateRenderer(Engine engine) {
        this.engine = engine;
    }

    public String render(String templateSource, Map<String, Object> data) {
        try {
            String source = resolveSource(templateSource);
            Template template = cache.computeIfAbsent(source, engine::parse);
            return template.data(data).render();
        } catch (TemplateException | IOException e) {
            throw new InternalException("Unable to render template: " + e.getMessage(), e);
        }
    }

    public void verify(String templateSource) {
        try {
            engine.parse(resolveSource(templateSource));
        } catch (TemplateException | IOException e) {
            throw new InternalException("Invalid template '" + excerpt(templateSource) + "': " + e.getMessage(), e);
        }
    }

    public Map<String, Object> requestData(MarketplaceOperation operation, MarketplaceRequest request) {
        Map<String, Object> requestMap = new LinkedHashMap<>();
        requestMap.put("identifier", request.identifier());
        requestMap.put("name", request.name());
        requestMap.put("operation", operation.name());
        requestMap.put("startDate", isoDate(request.startDate()));
        requestMap.put("endDate", isoDate(request.endDate()));
        requestMap.put("properties", request.properties() == null ? Map.of() : request.properties());
        requestMap.put("provider", providerMap(request));
        requestMap.put("consumer", partyMap(request.consumer()));
        requestMap.put("requester", partyMap(request.requester()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("request", requestMap);
        data.put("operation", operation.name());
        return data;
    }

    private String resolveSource(String templateSource) throws IOException {
        if (templateSource.startsWith(FILE_PREFIX)) {
            return Files.readString(Path.of(templateSource.substring(FILE_PREFIX.length())), StandardCharsets.UTF_8);
        }
        return templateSource;
    }

    private Map<String, Object> providerMap(MarketplaceRequest request) {
        Map<String, Object> provider = new LinkedHashMap<>();
        if (request.provider() != null) {
            provider.put("dataProductFqn", request.provider().dataProductFqn());
            List<String> ports = request.provider().dataProductPortsFqn();
            provider.put("dataProductPortsFqn", ports == null ? List.of() : ports);
        }
        return provider;
    }

    private Map<String, Object> partyMap(Party party) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (party != null) {
            map.put("type", party.type());
            map.put("identifier", party.identifier());
        }
        return map;
    }

    private String isoDate(Date date) {
        return date == null ? "" : date.toInstant().toString();
    }

    private String excerpt(String templateSource) {
        return templateSource.length() <= EXCERPT_LENGTH ? templateSource : templateSource.substring(0, EXCERPT_LENGTH);
    }
}
