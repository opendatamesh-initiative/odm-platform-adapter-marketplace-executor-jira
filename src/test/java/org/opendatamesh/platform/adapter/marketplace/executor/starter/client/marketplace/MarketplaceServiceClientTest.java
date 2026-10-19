package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.MarketplaceServiceConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class MarketplaceServiceClientTest {

    private final RestUtils restUtils = mock(RestUtils.class);
    private final MarketplaceServiceConfig config = mock(MarketplaceServiceConfig.class, RETURNS_DEEP_STUBS);
    private final MarketplaceResult result = new MarketplaceResult(MarketplaceRequestStatus.PENDING, "m",
            new Provider("urn:dp:x", List.of("p1")), new Date());

    private MarketplaceServiceClient client() {
        when(config.address()).thenReturn("http://marketplace");
        when(config.resultDelivery().maxAttempts()).thenReturn(3);
        when(config.resultDelivery().backoff()).thenReturn(Duration.ofMillis(1));
        return new MarketplaceServiceClient(restUtils, config, new ObjectMapper());
    }

    @Test
    void whenResultsEndpointReturns404ThenRetriedUntilSuccess() {
        when(restUtils.genericPost(anyString(), any(), any(), any()))
                .thenThrow(new ClientException(404, "not yet"))
                .thenThrow(new ClientException(503, "down"))
                .thenReturn(null);

        client().sendResult("id-1", result);

        verify(restUtils, times(3)).genericPost(anyString(), any(), any(), any());
    }

    @Test
    void whenResultsEndpointKeepsFailingThenInternalExceptionAfterMaxAttempts() {
        when(restUtils.genericPost(anyString(), any(), any(), any())).thenThrow(new ClientException(503, "down"));
        MarketplaceServiceClient client = client();

        InternalException e = assertThrows(InternalException.class, () -> client.sendResult("id-1", result));

        assertTrue(e.getMessage().startsWith("Unable to deliver result PENDING for request id-1"));
        verify(restUtils, times(3)).genericPost(anyString(), any(), any(), any());
    }

    @Test
    void whenResultsEndpointReturns400ThenNotRetried() {
        when(restUtils.genericPost(anyString(), any(), any(), any())).thenThrow(new ClientException(400, "bad"));
        MarketplaceServiceClient client = client();

        assertThrows(InternalException.class, () -> client.sendResult("id-1", result));

        verify(restUtils, times(1)).genericPost(anyString(), any(), any(), any());
    }
}
