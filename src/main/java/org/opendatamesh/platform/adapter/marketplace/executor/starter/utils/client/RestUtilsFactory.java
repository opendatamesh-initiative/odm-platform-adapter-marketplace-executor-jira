package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.Oauth2;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

public abstract class RestUtilsFactory {

    private RestUtilsFactory() {
    }

    public static RestUtils getRestUtils() {
        return wrapDefault().build();
    }

    public static RestUtils getRestUtils(HttpClient httpClient, ObjectMapper objectMapper) {
        return JavaHttpClientRestUtilsTemplate.wrap(httpClient, objectMapper).build();
    }

    public static RestUtils getAuthenticatedRestUtils(List<HttpHeader> authenticatedHeaders, Oauth2 oauth2) {
        return wrapDefault().authenticate(authenticatedHeaders, oauth2).build();
    }

    public static RestUtils getAuthenticatedRestUtils(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            List<HttpHeader> authenticatedHeaders,
            Oauth2 oauth2
    ) {
        return JavaHttpClientRestUtilsTemplate.wrap(httpClient, objectMapper)
                .authenticate(authenticatedHeaders, oauth2)
                .build();
    }

    public static RestUtils getAsyncRestUtils(String asyncEndpointRequest, String asyncEndpointPoll) {
        return wrapDefault().async(asyncEndpointRequest, asyncEndpointPoll).build();
    }

    public static RestUtils getAsyncRestUtils(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String asyncEndpointRequest,
            String asyncEndpointPoll
    ) {
        return JavaHttpClientRestUtilsTemplate.wrap(httpClient, objectMapper)
                .async(asyncEndpointRequest, asyncEndpointPoll)
                .build();
    }

    public static RestUtils getAuthenticatedAsyncRestUtils(
            List<HttpHeader> authenticatedHeaders,
            Oauth2 oauth2,
            String asyncEndpointRequest,
            String asyncEndpointPoll
    ) {
        return wrapDefault()
                .authenticate(authenticatedHeaders, oauth2)
                .async(asyncEndpointRequest, asyncEndpointPoll)
                .build();
    }

    public static RestUtils getAuthenticatedAsyncRestUtils(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            List<HttpHeader> authenticatedHeaders,
            Oauth2 oauth2,
            String asyncEndpointRequest,
            String asyncEndpointPoll
    ) {
        return JavaHttpClientRestUtilsTemplate.wrap(httpClient, objectMapper)
                .authenticate(authenticatedHeaders, oauth2)
                .async(asyncEndpointRequest, asyncEndpointPoll)
                .build();
    }

    private static JavaHttpClientRestUtilsTemplate wrapDefault() {
        return JavaHttpClientRestUtilsTemplate.wrap(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build(),
                new ObjectMapper()
        );
    }
}
