package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class RequestFilterServiceTest {

    private static final MarketplaceOperation OPERATION = MarketplaceOperation.MARKETPLACE_SUBSCRIBE;

    /**
     * Scenario: Request with only some ports inside the port filter is declined as a whole
     *     Given the port filter accepts only ports matching the regex "urn:dp:finance:sales:port:.*"
     *     When a request has one matching and one non-matching port
     *     Then the request is declined
     */
    @Test
    void whenSomePortsDoNotMatchThenRequestNotAccepted() {
        JiraConfig.Filter filter = emptyFilter();
        when(filter.portFqns()).thenReturn(Optional.of(List.of("urn:dp:finance:sales:port:.*")));
        RequestFilterService service = new RequestFilterService(configWith(filter));

        MarketplaceRequest request = request("USER", List.of("urn:dp:finance:sales:port:a", "urn:dp:finance:other:port:b"));

        assertFalse(service.accepts(OPERATION, request));
        assertEquals("port-fqns", service.describeMismatch(OPERATION, request));
        assertTrue(service.accepts(OPERATION, request("USER", List.of("urn:dp:finance:sales:port:a"))));
    }

    /**
     * Scenario: Filter patterns are full-match regular expressions
     *     Given the filter accepts consumer types matching the regex "(?i)user|team"
     *     When requests arrive with consumer types "USER", "team" and "dataproduct-user"
     *     Then "USER" and "team" are accepted
     *     And "dataproduct-user" is declined
     */
    @Test
    void whenRegexPatternsThenFullMatchApplied() {
        JiraConfig.Filter filter = emptyFilter();
        when(filter.consumerTypes()).thenReturn(Optional.of(List.of("(?i)user|team")));
        RequestFilterService service = new RequestFilterService(configWith(filter));

        assertTrue(service.accepts(OPERATION, request("USER", List.of("p1"))));
        assertTrue(service.accepts(OPERATION, request("team", List.of("p1"))));
        assertFalse(service.accepts(OPERATION, request("dataproduct-user", List.of("p1"))));
    }

    private JiraConfig configWith(JiraConfig.Filter filter) {
        JiraConfig config = mock(JiraConfig.class);
        when(config.filter()).thenReturn(filter);
        return config;
    }

    private JiraConfig.Filter emptyFilter() {
        JiraConfig.Filter filter = mock(JiraConfig.Filter.class);
        when(filter.operations()).thenReturn(Optional.empty());
        when(filter.providerDataProductFqns()).thenReturn(Optional.empty());
        when(filter.portFqns()).thenReturn(Optional.empty());
        when(filter.consumerTypes()).thenReturn(Optional.empty());
        when(filter.consumerIdentifiers()).thenReturn(Optional.empty());
        when(filter.requesterIdentifiers()).thenReturn(Optional.empty());
        when(filter.properties()).thenReturn(Map.of());
        return filter;
    }

    private MarketplaceRequest request(String consumerType, List<String> ports) {
        return new MarketplaceRequest("req", "id-1",
                new Provider("urn:dp:finance:sales", ports),
                new Party(consumerType, "consumer"),
                new Party("USER", "jdoe@acme.com"),
                Map.of(), null, null);
    }
}
