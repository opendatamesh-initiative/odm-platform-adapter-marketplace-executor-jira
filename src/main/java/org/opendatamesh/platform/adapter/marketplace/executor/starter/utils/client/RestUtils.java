package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientResourceMappingException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.http.HttpHeader;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories.Page;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories.PageRequest;

import java.io.File;
import java.util.List;

public interface RestUtils {

    <R, F> Page<R> getPage(String url, List<HttpHeader> httpHeaders, PageRequest pageable, F filters, Class<R> clazz)
            throws ClientException, ClientResourceMappingException;

    <R, F> R genericGet(String url, List<HttpHeader> httpHeaders, F filters, Class<R> clazz)
            throws ClientException, ClientResourceMappingException;

    <R, ID> R get(String url, List<HttpHeader> httpHeaders, ID identifier, Class<R> clazz)
            throws ClientException, ClientResourceMappingException;

    <R> R create(String url, List<HttpHeader> httpHeaders, R resourceToCreate, Class<R> clazz)
            throws ClientException, ClientResourceMappingException;

    <R, ID> R put(String url, List<HttpHeader> httpHeaders, ID identifier, R resourceToModify, Class<R> clazz)
            throws ClientException, ClientResourceMappingException;

    <R, ID> R patch(String url, List<HttpHeader> httpHeaders, ID identifier, R resourceToModify, Class<R> clazz)
            throws ClientException, ClientResourceMappingException;

    <I, O> O genericPatch(String url, List<HttpHeader> httpHeaders, I resourceToModify, Class<O> clazz)
            throws ClientException, ClientResourceMappingException;

    <ID> void delete(String url, List<HttpHeader> httpHeaders, ID identifier) throws ClientException;

    <I, O> O genericPost(String url, List<HttpHeader> httpHeaders, I resource, Class<O> clazz)
            throws ClientException, ClientResourceMappingException;

    File download(String url, List<HttpHeader> httpHeaders, Object resource, File storeLocation)
            throws ClientException, ClientResourceMappingException;
}
