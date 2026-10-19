package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraApi;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.client.ClientException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.MarketplaceResponseRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
@QuarkusTest
class JiraIssueEventUseCaseControllerTest {

    private static final String NATIVE_PATH = "/api/v1/up/marketplace-executor/webhooks/jira/native-events";
    private static final String PATH = "/api/v1/up/marketplace-executor/webhooks/jira/issue-events";

    @InjectMock
    RestUtils restUtils;

    @InjectMock
    @JiraApi
    RestUtils jiraRestUtils;

    private final ObjectMapper mapper = new ObjectMapper();

    private void registeredRequest(String uuid, String operation, boolean finalResultExists) throws Exception {
        JsonNode registered = mapper.readTree("""
                {"uuid":"%s","operation":"%s","name":"n","providerDataProductFqn":"urn:dp:finance:sales",
                 "providerDataProductPortsFqn":["p1","p2"],"consumerType":"user","consumerIdentifier":"a@b.c",
                 "requesterType":"user","requesterIdentifier":"a@b.c","properties":{}}
                """.formatted(uuid, operation));
        JsonNode responses = mapper.readTree("{\"content\":[{\"status\":\"PENDING\"}" + (finalResultExists ? ",{\"status\":\"GRANTED\"}" : "") + "]}");
        when(restUtils.genericGet(endsWith("/api/v1/pp/marketplace/requests/" + uuid), any(), any(), eq(JsonNode.class))).thenReturn(registered);
        when(restUtils.genericGet(contains("/executor-responses"), any(), any(), eq(JsonNode.class))).thenReturn(responses);
    }

    private String event(String correlation, String statusCategory, String resolution) {
        return """
                {%s"fields":{"key":"ACC-1","id":"10001","status":"Done","statusCategory":"%s","resolution":"%s","actor":"Jane Doe","lastComment":"ok"}}
                """.formatted(correlation == null ? "" : "\"correlationId\":\"" + correlation + "\",", statusCategory, resolution);
    }

    private String payload(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/jira/" + name)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Scenario: Done issue with a fulfilled resolution grants access for all ports
     *   Given request "id-1" is a MARKETPLACE_SUBSCRIBE registered in the Marketplace Service with ports "p1" and "p2"
     *   And no final result exists for "id-1"
     *   When Jira Automation sends an issue event for "id-1" with statusCategory "done" and resolution "Done"
     *   Then a GRANTED result with ports "p1" and "p2" is posted for "id-1"
     *   And the endpoint answers 200 with outcome "REPORTED" and status "GRANTED"
     */
    @Test
    void whenIssueDoneThenGrantedWithAllPorts() throws Exception {
        registeredRequest("id-1", "MARKETPLACE_SUBSCRIBE", false);

        given().contentType(ContentType.JSON).body(event("id-1", "done", "Done"))
                .when().post(PATH)
                .then().statusCode(200)
                .body("outcome", equalTo("REPORTED"))
                .body("status", equalTo("GRANTED"))
                .body("reason", nullValue());

        ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);
        verify(restUtils).genericPost(endsWith("/api/v1/pp/marketplace/requests/id-1/results"), any(), result.capture(), eq(Void.class));
        MarketplaceResponseRes posted = (MarketplaceResponseRes) result.getValue();
        assertEquals(MarketplaceRequestStatus.GRANTED, posted.getStatus());
        assertEquals(List.of("p1", "p2"), posted.getProvider().getDataProductPortsFqn());
        assertTrue(posted.getMessage().contains("ACC-1"));
    }

    /**
     * Scenario: First final status wins after a reopen
     *   Given request "id-1" already has a GRANTED result in the Marketplace Service
     *   When Jira Automation sends another done issue event for "id-1" with resolution "Won't Do"
     *   Then no result is posted
     *   And the endpoint answers 200 with outcome "IGNORED" and reason "FINAL_RESULT_ALREADY_REPORTED"
     */
    @Test
    void whenFinalResultExistsThenEventIgnored() throws Exception {
        registeredRequest("id-1", "MARKETPLACE_SUBSCRIBE", true);

        given().contentType(ContentType.JSON).body(event("id-1", "done", "Won't Do"))
                .when().post(PATH)
                .then().statusCode(200)
                .body("outcome", equalTo("IGNORED"))
                .body("reason", equalTo("FINAL_RESULT_ALREADY_REPORTED"))
                .body("status", nullValue());

        verify(restUtils, never()).genericPost(any(), any(), any(), any());
    }

    /**
     * Scenario: Missing correlation is ignored
     *   When Jira Automation sends an issue event without correlationId
     *   Then no result is posted and the reason is "MISSING_CORRELATION"
     */
    @Test
    void whenCorrelationMissingThenIgnored() {
        given().contentType(ContentType.JSON).body(event(null, "done", "Done"))
                .when().post(PATH)
                .then().statusCode(200)
                .body("outcome", equalTo("IGNORED"))
                .body("reason", equalTo("MISSING_CORRELATION"));

        verifyNoInteractions(restUtils);
    }

    /**
     * Scenario: Missing correlation is ignored
     *   When Jira Automation sends an issue event without correlationId
     *   Then no result is posted and the reason is "MISSING_CORRELATION"
     * (variant: empty request body)
     */
    @Test
    void whenBodyEmptyThenIgnoredAsMissingCorrelation() {
        given().contentType(ContentType.JSON)
                .when().post(PATH)
                .then().statusCode(200)
                .body("outcome", equalTo("IGNORED"))
                .body("reason", equalTo("MISSING_CORRELATION"));

        verifyNoInteractions(restUtils);
    }

    /**
     * Scenario: Marketplace unavailable makes the endpoint fail for retry
     *   Given request "id-1" is a registered MARKETPLACE_SUBSCRIBE without final result
     *   And the Marketplace Service results endpoint keeps answering 503
     *   When Jira Automation sends a fulfilled issue event for "id-1"
     *   Then the endpoint answers 500
     */
    @Test
    void whenMarketplaceUnavailableThenServerError() throws Exception {
        registeredRequest("id-1", "MARKETPLACE_SUBSCRIBE", false);
        when(restUtils.genericPost(endsWith("/api/v1/pp/marketplace/requests/id-1/results"), any(), any(), eq(Void.class)))
                .thenThrow(new ClientException(503, "unavailable"));

        given().contentType(ContentType.JSON).body(event("id-1", "done", "Done"))
                .when().post(PATH)
                .then().statusCode(500)
                .body("error", equalTo("InternalException"))
                .body("message", containsString("Unable to deliver result GRANTED for request id-1"));
    }

    /**
     * Scenario: Native status transition to Done grants access
     *   Given request "id-1" is a registered MARKETPLACE_SUBSCRIBE without final result
     *   When a native "jira:issue_updated" payload arrives for an issue whose "customfield_10050" is "id-1", with a status changelog to a "done" status and resolution "Done"
     *   Then a GRANTED result is posted for "id-1"
     */
    @Test
    void whenNativeStatusTransitionToDoneThenGranted() throws Exception {
        registeredRequest("id-1", "MARKETPLACE_SUBSCRIBE", false);

        given().contentType(ContentType.JSON).body(payload("cloud-issue-updated-status.json"))
                .when().post(NATIVE_PATH)
                .then().statusCode(200)
                .body("outcome", equalTo("REPORTED"))
                .body("status", equalTo("GRANTED"));

        ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);
        verify(restUtils).genericPost(endsWith("/api/v1/pp/marketplace/requests/id-1/results"), any(), result.capture(), eq(Void.class));
        MarketplaceResponseRes posted = (MarketplaceResponseRes) result.getValue();
        assertEquals(MarketplaceRequestStatus.GRANTED, posted.getStatus());
        assertTrue(posted.getMessage().contains("Access granted in Snowflake"));
    }

    /**
     * Scenario: Native edit that does not change the status is ignored without lookups
     *   When a native "jira:issue_updated" payload without a status changelog item arrives
     *   Then no Marketplace Service call is made
     *   And the endpoint answers 200 with outcome "IGNORED" and reason "NOT_A_STATUS_TRANSITION"
     */
    @Test
    void whenNativeEditWithoutStatusChangeThenIgnoredWithoutLookups() throws Exception {
        given().contentType(ContentType.JSON).body(payload("cloud-issue-updated-no-status.json"))
                .when().post(NATIVE_PATH)
                .then().statusCode(200)
                .body("outcome", equalTo("IGNORED"))
                .body("reason", equalTo("NOT_A_STATUS_TRANSITION"));

        verifyNoInteractions(restUtils, jiraRestUtils);
    }
}
