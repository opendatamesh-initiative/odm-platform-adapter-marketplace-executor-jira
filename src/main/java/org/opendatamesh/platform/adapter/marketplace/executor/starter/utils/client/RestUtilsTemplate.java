package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpEntity;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpMethod;

import java.io.File;
import java.util.List;
import java.util.Map;

interface RestUtilsTemplate {

    <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables)
            throws ClientException;

    File download(String url, List<HttpHeader> httpHeaders, Object resource, File storeLocation) throws ClientException;
}
