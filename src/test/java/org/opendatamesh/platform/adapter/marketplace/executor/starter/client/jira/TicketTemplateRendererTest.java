package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// BDMD-5340: Jira marketplace executor
@QuarkusTest
class TicketTemplateRendererTest {

    @Inject
    TicketTemplateRenderer renderer;

    /**
     * Scenario: Missing template values render empty instead of failing
     *   Given a field template referencing "request.properties.costCenter"
     *   And the request has no "costCenter" property
     *   When the issue fields are rendered
     *   Then the field is rendered without error and without the missing value
     */
    @Test
    void whenPropertyMissingThenRendersEmpty() {
        MarketplaceRequest request = new MarketplaceRequest("n", "id-1",
                new Provider("urn:dp:finance:sales", List.of("p1")),
                new Party("user", "a@b.c"), new Party("user", "a@b.c"), Map.of(), null, null);
        Map<String, Object> data = renderer.requestData(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, request);

        String rendered = renderer.render("Cost center [{request.properties.costCenter}] for {request.identifier}", data);

        assertEquals("Cost center [] for id-1", rendered);
    }
}
