package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientResourceMappingException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpEntity;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpMethod;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories.Page;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories.PageRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories.Sort;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BaseRestUtils implements RestUtils {

    protected final RestUtilsTemplate rest;
    protected final ObjectMapper objectMapper;

    public BaseRestUtils(RestUtilsTemplate restUtilsTemplate) {
        this.rest = restUtilsTemplate;
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public <R, F> Page<R> getPage(String url, List<HttpHeader> httpHeaders, PageRequest pageable, F filters, Class<R> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            UriComponents uriComponents = new UriComponents(url);
            if (filters != null) {
                buildUriComponentFromFilters(uriComponents, filters);
            }
            if (pageable != null) {
                buildUriComponentFromPageable(uriComponents, pageable);
            }

            JsonNode response = rest.exchange(
                    uriComponents.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(httpHeaders),
                    JsonNode.class,
                    uriComponents.getUriVariables()
            );
            return toPage(response, clazz);
        } catch (IllegalArgumentException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <R, F> R genericGet(String url, List<HttpHeader> httpHeaders, F filters, Class<R> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            UriComponents uriComponents = new UriComponents(url);
            if (filters != null) {
                buildUriComponentFromFilters(uriComponents, filters);
            }

            JsonNode response = rest.exchange(
                    uriComponents.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(httpHeaders),
                    JsonNode.class,
                    uriComponents.getUriVariables()
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <R, ID> R get(String url, List<HttpHeader> httpHeaders, ID identifier, Class<R> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            JsonNode response = rest.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(httpHeaders),
                    JsonNode.class,
                    idVariables(identifier)
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <R> R create(String url, List<HttpHeader> httpHeaders, R resourceToCreate, Class<R> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            JsonNode response = rest.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(resourceToCreate, httpHeaders),
                    JsonNode.class,
                    new HashMap<>()
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <R, ID> R put(String url, List<HttpHeader> httpHeaders, ID identifier, R resourceToModify, Class<R> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            JsonNode response = rest.exchange(
                    url,
                    HttpMethod.PUT,
                    new HttpEntity<>(resourceToModify, httpHeaders),
                    JsonNode.class,
                    idVariables(identifier)
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <R, ID> R patch(String url, List<HttpHeader> httpHeaders, ID identifier, R resourceToModify, Class<R> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            JsonNode response = rest.exchange(
                    url,
                    HttpMethod.PATCH,
                    new HttpEntity<>(resourceToModify, httpHeaders),
                    JsonNode.class,
                    idVariables(identifier)
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <I, O> O genericPatch(String url, List<HttpHeader> httpHeaders, I resourceToModify, Class<O> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            JsonNode response = rest.exchange(
                    url,
                    HttpMethod.PATCH,
                    new HttpEntity<>(resourceToModify, httpHeaders),
                    JsonNode.class,
                    new HashMap<>()
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public <ID> void delete(String url, List<HttpHeader> httpHeaders, ID identifier) throws ClientException {
        rest.exchange(
                url,
                HttpMethod.DELETE,
                new HttpEntity<>(httpHeaders),
                JsonNode.class,
                idVariables(identifier)
        );
    }

    @Override
    public <I, O> O genericPost(String url, List<HttpHeader> httpHeaders, I resource, Class<O> clazz)
            throws ClientException, ClientResourceMappingException {
        try {
            JsonNode response = rest.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(resource, httpHeaders),
                    JsonNode.class,
                    new HashMap<>()
            );
            return objectMapper.treeToValue(response, clazz);
        } catch (JsonProcessingException e) {
            throw new ClientResourceMappingException(e.getMessage(), e);
        }
    }

    @Override
    public File download(String url, List<HttpHeader> httpHeaders, Object resource, File storeLocation)
            throws ClientException, ClientResourceMappingException {
        return rest.download(url, httpHeaders, resource, storeLocation);
    }

    private Map<String, Object> idVariables(Object identifier) {
        Map<String, Object> uriVariables = new HashMap<>();
        if (identifier != null) {
            uriVariables.put("id", identifier);
        }
        return uriVariables;
    }

    private <R> Page<R> toPage(JsonNode response, Class<R> clazz) {
        if (response == null || response.isNull()) {
            return new Page<>(List.of(), 0, 0, 0);
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        JsonNode contentNode = response.get("content");
        List<R> content = contentNode == null || contentNode.isNull()
                ? List.of()
                : objectMapper.convertValue(contentNode, listType);
        int number = response.path("number").asInt(0);
        int size = response.path("size").asInt(0);
        long totalElements = response.path("totalElements").asLong(0);
        return new Page<>(content, number, size, totalElements);
    }

    private void buildUriComponentFromPageable(UriComponents uriComponents, PageRequest pageable) {
        uriComponents.queryParam("page", "{page}");
        uriComponents.getUriVariables().put("page", pageable.getPage());

        uriComponents.queryParam("size", "{size}");
        uriComponents.getUriVariables().put("size", pageable.getSize());

        StringBuilder sortBuilder = new StringBuilder();
        for (Sort.Order order : pageable.getSort().getOrders()) {
            if (sortBuilder.length() > 0) {
                sortBuilder.append(",");
            }
            sortBuilder.append(order.getProperty()).append(",").append(order.getDirection());
        }
        uriComponents.queryParam("sort", "{sort}");
        uriComponents.getUriVariables().put("sort", sortBuilder.toString());
    }

    private <F> void buildUriComponentFromFilters(UriComponents uriComponents, F filters) {
        try {
            for (Field field : filters.getClass().getDeclaredFields()) {
                boolean accessible = field.canAccess(filters);
                field.setAccessible(true);
                Object value = field.get(filters);
                String paramName = field.getName();

                if (value instanceof Collection<?> collection) {
                    List<String> placeholders = new ArrayList<>();
                    int index = 0;
                    for (Object item : collection) {
                        String placeholder = paramName + index;
                        placeholders.add("{" + placeholder + "}");
                        uriComponents.getUriVariables().put(placeholder, item);
                        index++;
                    }
                    uriComponents.queryParam(paramName, placeholders.toArray());
                } else if (value != null && value.getClass().isArray()) {
                    int length = Array.getLength(value);
                    List<String> placeholders = new ArrayList<>();
                    for (int i = 0; i < length; i++) {
                        Object item = Array.get(value, i);
                        String placeholder = paramName + i;
                        placeholders.add("{" + placeholder + "}");
                        uriComponents.getUriVariables().put(placeholder, item);
                    }
                    uriComponents.queryParam(paramName, placeholders.toArray());
                } else {
                    uriComponents.queryParam(paramName, "{" + paramName + "}");
                    uriComponents.getUriVariables().put(paramName, value);
                }
                field.setAccessible(accessible);
            }
        } catch (Exception e) {
            throw new InternalException(e.getMessage(), e);
        }
    }

    private static class UriComponents {
        private final StringBuilder url;
        private final Map<String, Object> uriVariables = new HashMap<>();
        private boolean queryStarted;

        UriComponents(String baseUrl) {
            this.url = new StringBuilder(baseUrl);
            this.queryStarted = baseUrl.contains("?");
        }

        void queryParam(String name, Object... placeholders) {
            if (placeholders == null || placeholders.length == 0) {
                return;
            }
            for (Object placeholder : placeholders) {
                url.append(queryStarted ? '&' : '?');
                queryStarted = true;
                url.append(name).append('=').append(placeholder);
            }
        }

        String toUriString() {
            return url.toString();
        }

        Map<String, Object> getUriVariables() {
            return uriVariables;
        }
    }
}
