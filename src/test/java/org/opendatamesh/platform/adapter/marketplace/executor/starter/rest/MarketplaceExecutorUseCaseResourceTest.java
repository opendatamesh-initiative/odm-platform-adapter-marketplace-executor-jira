package org.opendatamesh.platform.adapter.marketplace.executor.starter.rest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
class MarketplaceExecutorUseCaseResourceTest {

    private static final String PATH = "/api/v1/up/marketplace-executor/requests";

    @InjectMock
    RestUtils restUtils;

    @Test
    void validSubscribeRequestIsAccepted() {
        given().contentType(ContentType.JSON).body(body("MARKETPLACE_SUBSCRIBE", "2025-01-01T00:00:00.000+0000", "2025-12-31T00:00:00.000+0000"))
                .when().post(PATH)
                .then().statusCode(202);

        verify(restUtils, timeout(5000)).genericPost(endsWith("/api/v1/pp/marketplace/requests/id-1/results"), any(), any(), eq(Void.class));
    }

    @Test
    void unknownOperationIsRejected() {
        given().contentType(ContentType.JSON).body(body("MARKETPLACE_FOO", "2025-01-01T00:00:00.000+0000", "2025-12-31T00:00:00.000+0000"))
                .when().post(PATH)
                .then().statusCode(400)
                .body("error", equalTo("BadRequestException"))
                .body("message", containsString("Invalid operation type"));

        verifyNoInteractions(restUtils);
    }

    @Test
    void startAfterEndIsRejected() {
        given().contentType(ContentType.JSON).body(body("MARKETPLACE_SUBSCRIBE", "2026-01-01T00:00:00.000+0000", "2025-01-01T00:00:00.000+0000"))
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

    private String body(String operation, String start, String end) {
        return """
                {"v":"1","operation":"%s","request":{"name":"n","identifier":"id-1",
                "provider":{"dataProductFqn":"dp","dataProductPortsFqn":["p"]},
                "consumer":{"type":"user","identifier":"a@b.c"},
                "requester":{"type":"user","identifier":"a@b.c"},
                "startDate":"%s","endDate":"%s"}}
                """.formatted(operation, start, end);
    }
}
