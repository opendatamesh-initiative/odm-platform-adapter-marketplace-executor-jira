package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpEntity;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpMethod;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.MediaType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.Oauth2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class JavaHttpClientRestUtilsTemplate implements RestUtilsTemplate {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private List<HttpHeader> authenticatedHeaders;
    private Oauth2 oauth2;
    private String asyncEndpointRequest;
    private String asyncEndpointPoll;

    private JavaHttpClientRestUtilsTemplate(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    static JavaHttpClientRestUtilsTemplate wrap(HttpClient httpClient, ObjectMapper objectMapper) {
        return new JavaHttpClientRestUtilsTemplate(httpClient, objectMapper);
    }

    JavaHttpClientRestUtilsTemplate authenticate(List<HttpHeader> authenticatedHeaders, Oauth2 oauth2) {
        this.authenticatedHeaders = authenticatedHeaders;
        this.oauth2 = oauth2;
        return this;
    }

    JavaHttpClientRestUtilsTemplate async(String asyncEndpointRequest, String asyncEndpointPoll) {
        this.asyncEndpointRequest = asyncEndpointRequest;
        this.asyncEndpointPoll = asyncEndpointPoll;
        return this;
    }

    RestUtils build() {
        RestUtilsTemplate template = this;

        if (authenticatedHeaders != null || oauth2 != null) {
            template = new AuthenticatedRestUtilsTemplate(template, authenticatedHeaders, oauth2);
        }

        if (asyncEndpointRequest != null && asyncEndpointPoll != null) {
            template = new AsyncRestUtilsTemplate(template, asyncEndpointRequest, asyncEndpointPoll);
        }

        return new BaseRestUtils(template);
    }

    @Override
    public <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables)
            throws ClientException {
        return exchangeExpanded(expand(url, uriVariables), method, requestEntity, responseType);
    }

    @Override
    public File download(String url, List<HttpHeader> httpHeaders, Object resource, File storeLocation) throws ClientException {
        try {
            HttpEntity<?> entity = new HttpEntity<>(resource, httpHeaders);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .method(HttpMethod.POST.name(), bodyPublisher(entity));
            applyHeaders(builder, entity);
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (response.statusCode() >= 400) {
                throw new ClientException(response.statusCode(), new String(body, StandardCharsets.UTF_8));
            }
            try (FileOutputStream fos = new FileOutputStream(storeLocation)) {
                fos.write(body);
            }
            return storeLocation;
        } catch (ClientException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException(500, e.getMessage());
        } catch (IOException e) {
            throw new ClientException(500, e.getMessage());
        }
    }

    private <T> T exchangeExpanded(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType)
            throws ClientException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .method(method.name(), bodyPublisher(requestEntity));
            applyHeaders(builder, requestEntity);

            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (response.statusCode() >= 400) {
                throw new ClientException(response.statusCode(), new String(body, StandardCharsets.UTF_8));
            }
            if (responseType == null || body.length == 0 || Void.class.equals(responseType) || void.class.equals(responseType)) {
                return null;
            }
            if (JsonNode.class.equals(responseType)) {
                return responseType.cast(objectMapper.readTree(body));
            }
            return objectMapper.readValue(body, responseType);
        } catch (ClientException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException(500, e.getMessage());
        } catch (IOException e) {
            throw new ClientException(500, e.getMessage());
        }
    }

    private void applyHeaders(HttpRequest.Builder builder, HttpEntity<?> requestEntity) {
        boolean hasContentType = false;
        if (requestEntity != null) {
            for (HttpHeader header : requestEntity.getHeaders()) {
                builder.header(header.getName(), header.getValue());
                if (HttpHeader.CONTENT_TYPE.equalsIgnoreCase(header.getName())) {
                    hasContentType = true;
                }
            }
        }
        if (!hasContentType && requestEntity != null && requestEntity.getBody() != null && !isFormUrlEncoded(requestEntity)) {
            builder.header(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON.getFullType());
        }
    }

    private HttpRequest.BodyPublisher bodyPublisher(HttpEntity<?> requestEntity) throws IOException {
        if (requestEntity == null || requestEntity.getBody() == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        Object body = requestEntity.getBody();
        if (isFormUrlEncoded(requestEntity)) {
            return HttpRequest.BodyPublishers.ofString(encodeForm(body));
        }
        if (body instanceof byte[] bytes) {
            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        }
        if (body instanceof String text) {
            return HttpRequest.BodyPublishers.ofString(text);
        }
        return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
    }

    private boolean isFormUrlEncoded(HttpEntity<?> requestEntity) {
        if (requestEntity == null) {
            return false;
        }
        String formType = MediaType.APPLICATION_FORM_URLENCODED.getFullType();
        for (HttpHeader header : requestEntity.getHeaders()) {
            if (HttpHeader.CONTENT_TYPE.equalsIgnoreCase(header.getName())
                    && header.getValue() != null
                    && header.getValue().startsWith(formType)) {
                return true;
            }
        }
        return false;
    }

    private String encodeForm(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            return String.valueOf(body);
        }
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    appendFormField(encoded, entry.getKey(), item);
                }
            } else {
                appendFormField(encoded, entry.getKey(), value);
            }
        }
        return encoded.toString();
    }

    private void appendFormField(StringBuilder encoded, Object key, Object value) {
        if (encoded.length() > 0) {
            encoded.append('&');
        }
        encoded.append(encode(key)).append('=').append(encode(value));
    }

    private String expand(String url, Map<String, ?> uriVariables) {
        if (url == null || uriVariables == null || uriVariables.isEmpty()) {
            return url;
        }
        String expanded = url;
        for (Map.Entry<String, ?> entry : uriVariables.entrySet()) {
            expanded = expanded.replace("{" + entry.getKey() + "}", encode(entry.getValue()));
        }
        return expanded;
    }

    private String encode(Object value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
