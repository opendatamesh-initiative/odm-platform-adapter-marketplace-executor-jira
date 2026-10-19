package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraApi;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.rest.v1.resources.marketplace.MarketplaceResponseRes;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// BDMD-5340: Jira marketplace executor
@QuarkusTest
class MarketplaceExecutorUseCaseControllerTest {

    private static final String PATH = "/api/v1/up/marketplace-executor/requests";

    @InjectMock
    RestUtils restUtils;

    @InjectMock
    @JiraApi
    RestUtils jiraRestUtils;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Scenario: Subscribe request opens a Jira issue and reports PENDING
     *   Given the subscribe profile uses the Issue API on project "ACC" with issue type "Task"
     *   And Jira answers with issue key "ACC-1"
     *   When the Marketplace Service submits a MARKETPLACE_SUBSCRIBE request "id-1" for data product "urn:dp:finance:sales"
     *   Then the executor answers 202
     *   And an issue is created whose field "customfield_10050" is "id-1"
     *   And a PENDING result mentioning "ACC-1" is posted for request "id-1"
     */
    @Test
    void whenSubscribeRequestThenIssueOpenedAndPendingReported() throws Exception {
        when(jiraRestUtils.genericPost(contains("/rest/api/2/issue"), any(), any(), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("{\"id\":\"10001\",\"key\":\"ACC-1\"}"));

        given().contentType(ContentType.JSON).body(body("MARKETPLACE_SUBSCRIBE", "id-1", "a@b.c", "2025-01-01T00:00:00.000+0000", "2025-12-31T00:00:00.000+0000"))
                .when().post(PATH)
                .then().statusCode(202);

        ArgumentCaptor<Object> issue = ArgumentCaptor.forClass(Object.class);
        verify(jiraRestUtils, timeout(5000)).genericPost(contains("/rest/api/2/issue"), any(), issue.capture(), eq(JsonNode.class));
        assertEquals("id-1", ((JsonNode) issue.getValue()).at("/fields/customfield_10050").asText());
        assertEquals("ACC", ((JsonNode) issue.getValue()).at("/fields/project/key").asText());
        MarketplaceResponseRes pending = capturePendingResult("id-1");
        assertEquals(MarketplaceRequestStatus.PENDING, pending.getStatus());
        assertTrue(pending.getMessage().contains("ACC-1"));
    }

    /**
     * Scenario: Unsubscribe request raises a JSM request on behalf of the requester
     *   Given the unsubscribe profile uses the Service Management Request API on service desk "1" and request type "10"
     *   And the requester "jane@acme.com" is the Jira Cloud user with accountId "acc-42"
     *   When the Marketplace Service submits a MARKETPLACE_UNSUBSCRIBE request "id-2" requested by "jane@acme.com"
     *   Then a request is raised with raiseOnBehalfOf "acc-42" and request field "customfield_10050" set to "id-2"
     *   And a PENDING result is posted for request "id-2"
     */
    @Test
    void whenUnsubscribeRequestThenJsmRequestRaisedOnBehalfOfRequester() throws Exception {
        when(jiraRestUtils.genericGet(contains("/rest/api/2/user/search"), any(), any(), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("[{\"accountId\":\"acc-42\",\"emailAddress\":\"jane@acme.com\",\"active\":true}]"));
        when(jiraRestUtils.genericPost(contains("/rest/servicedeskapi/request"), any(), any(), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("{\"issueId\":\"10002\",\"issueKey\":\"SD-1\"}"));

        given().contentType(ContentType.JSON).body(body("MARKETPLACE_UNSUBSCRIBE", "id-2", "jane@acme.com", "2025-01-01T00:00:00.000+0000", "2025-12-31T00:00:00.000+0000"))
                .when().post(PATH)
                .then().statusCode(202);

        ArgumentCaptor<Object> raised = ArgumentCaptor.forClass(Object.class);
        verify(jiraRestUtils, timeout(5000)).genericPost(contains("/rest/servicedeskapi/request"), any(), raised.capture(), eq(JsonNode.class));
        JsonNode request = (JsonNode) raised.getValue();
        assertEquals("acc-42", request.get("raiseOnBehalfOf").asText());
        assertEquals("id-2", request.at("/requestFieldValues/customfield_10050").asText());
        assertEquals(MarketplaceRequestStatus.PENDING, capturePendingResult("id-2").getStatus());
    }

    /**
     * Scenario: Request outside the filter is declined without side effects
     *   Given the filter accepts only data products matching the regex "urn:dp:finance:.*"
     *   When the Marketplace Service submits a request for data product "urn:dp:hr:payroll"
     *   Then the executor answers 409 with error "RequestNotHandledException"
     *   And no Jira call and no result post happen
     */
    @Test
    void whenRequestOutsideFilterThenConflictAndNoSideEffects() {
        given().contentType(ContentType.JSON)
                .body(body("MARKETPLACE_SUBSCRIBE", "id-1", "a@b.c", "2025-01-01T00:00:00.000+0000", "2025-12-31T00:00:00.000+0000")
                        .replace("urn:dp:finance:sales", "urn:dp:hr:payroll"))
                .when().post(PATH)
                .then().statusCode(409)
                .body("error", equalTo("RequestNotHandledException"));

        verifyNoInteractions(restUtils, jiraRestUtils);
    }

    private MarketplaceResponseRes capturePendingResult(String uuid) {
        ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);
        verify(restUtils, timeout(5000)).genericPost(endsWith("/api/v1/pp/marketplace/requests/" + uuid + "/results"), any(), result.capture(), eq(Void.class));
        return (MarketplaceResponseRes) result.getValue();
    }

    @Test
    void unknownOperationIsRejected() {
        given().contentType(ContentType.JSON).body(body("MARKETPLACE_FOO", "id-1", "a@b.c", "2025-01-01T00:00:00.000+0000", "2025-12-31T00:00:00.000+0000"))
                .when().post(PATH)
                .then().statusCode(400)
                .body("error", equalTo("BadRequestException"))
                .body("message", containsString("Invalid operation type"));

        verifyNoInteractions(restUtils);
    }

    @Test
    void startAfterEndIsRejected() {
        given().contentType(ContentType.JSON).body(body("MARKETPLACE_SUBSCRIBE", "id-1", "a@b.c", "2026-01-01T00:00:00.000+0000", "2025-01-01T00:00:00.000+0000"))
                .when().post(PATH)
                .then().statusCode(400)
                .body("message", equalTo("Start date must be before end date"));

        verifyNoInteractions(restUtils);
    }

    @Test
    void missingFieldsAreRejectedByValidation() {
        given().contentType(ContentType.JSON).body("{\"v\":\"1\"}")
                .when().post(PATH)
                .then().statusCode(400)
                .body("error", equalTo("BadRequest"))
                .body("path", equalTo(PATH));
    }

    private String body(String operation, String id, String requester, String start, String end) {
        return """
                {"v":"1","operation":"%s","request":{"name":"n","identifier":"%s",
                "provider":{"dataProductFqn":"urn:dp:finance:sales","dataProductPortsFqn":["p"]},
                "consumer":{"type":"user","identifier":"a@b.c"},
                "requester":{"type":"user","identifier":"%s"},
                "startDate":"%s","endDate":"%s"}}
                """.formatted(operation, id, requester, start, end);
    }
}
