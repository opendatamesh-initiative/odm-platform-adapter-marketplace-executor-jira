package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceOperation;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceResult;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.RegisteredAccessRequest;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEvent;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEventIgnoredReason;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcome;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketOutcomeCause;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// BDMD-5340: Jira marketplace executor
class CompleteTicketTest {

    private final List<MarketplaceResult> posted = new ArrayList<>();
    private MarketplaceRequestStatus reported;
    private TicketEventIgnoredReason ignored;
    private Optional<RegisteredAccessRequest> registered = Optional.empty();
    private Optional<TicketOutcome> outcome = Optional.empty();
    private boolean outcomeEvaluated;
    private final List<Map<String, String>> enrichedFrom = new ArrayList<>();
    private final List<String> marketplaceCalls = new ArrayList<>();

    private final CompleteTicketPresenter presenter = new CompleteTicketPresenter() {
        @Override
        public void presentReported(MarketplaceRequestStatus status) {
            reported = status;
        }

        @Override
        public void presentIgnored(TicketEventIgnoredReason reason) {
            ignored = reason;
        }
    };

    private final CompleteTicketMarketplaceOutboundPort marketplace = new CompleteTicketMarketplaceOutboundPort() {
        @Override
        public Optional<RegisteredAccessRequest> findAccessRequest(String uuid) {
            marketplaceCalls.add("find");
            return registered;
        }

        @Override
        public boolean hasFinalResult(String uuid) {
            return false;
        }

        @Override
        public void sendFinalResult(String uuid, MarketplaceResult result) {
            posted.add(result);
        }
    };

    private RegisteredAccessRequest register(MarketplaceOperation operation, String uuid) {
        MarketplaceRequest request = new MarketplaceRequest("n", uuid, new Provider("urn:dp:finance:sales", List.of("p1", "p2")),
                new Party("user", "a@b.c"), new Party("user", "a@b.c"), Map.of(), new Date(), null);
        RegisteredAccessRequest access = new RegisteredAccessRequest(operation, request);
        registered = Optional.of(access);
        return access;
    }

    private void run(String uuid, Map<String, String> fields) {
        run(new TicketEvent(uuid, fields));
    }

    private void run(TicketEvent event) {
        new CompleteTicket(
                new CompleteTicketCommand(event),
                presenter,
                marketplace,
                (operation, request) -> true,
                (operation, eventFields) -> {
                    outcomeEvaluated = true;
                    return outcome;
                },
                (operation, request, found, status, eventFields) ->
                        status + "|" + eventFields.getOrDefault("lastComment", "") + "|" + eventFields.getOrDefault("actor", ""),
                eventFields -> {
                    assertTrue(outcomeEvaluated, "the last comment must be fetched only after an outcome was found");
                    enrichedFrom.add(eventFields);
                    Map<String, String> enriched = new java.util.LinkedHashMap<>(eventFields);
                    enriched.putIfAbsent("lastComment", "Access granted in Snowflake");
                    return enriched;
                }
        ).execute();
    }

    /**
     * Scenario: Won't Do resolution denies the subscribe request
     *   Given request "id-1" is a registered MARKETPLACE_SUBSCRIBE without final result
     *   When Jira Automation sends an issue event for "id-1" with statusCategory "done", resolution "Won't Do" and lastComment "Not authorised"
     *   Then a DENIED result whose message contains "Not authorised" is posted
     */
    @Test
    void whenSubscribeIssueRefusedThenDenied() {
        register(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, "id-1");
        outcome = Optional.of(new TicketOutcome(TicketOutcomeCause.REFUSED, null));

        run("id-1", Map.of("statusCategory", "done", "resolution", "Won't Do", "lastComment", "Not authorised"));

        assertEquals(1, posted.size());
        assertEquals(MarketplaceRequestStatus.DENIED, posted.get(0).status());
        assertTrue(posted.get(0).message().contains("Not authorised"));
        assertEquals(MarketplaceRequestStatus.DENIED, reported);
        assertNull(ignored);
    }

    /**
     * Scenario: Refused unsubscribe is reported as a descriptive ERROR
     *   Given request "id-2" is a registered MARKETPLACE_UNSUBSCRIBE without final result
     *   When Jira Automation sends an issue event for "id-2" with status "Declined" and actor "Jane Doe"
     *   Then an ERROR result whose message states the request was refused on Jira by "Jane Doe" is posted
     */
    @Test
    void whenUnsubscribeIssueRefusedThenDescriptiveError() {
        register(MarketplaceOperation.MARKETPLACE_UNSUBSCRIBE, "id-2");
        outcome = Optional.of(new TicketOutcome(TicketOutcomeCause.REFUSED, null));

        run("id-2", Map.of("status", "Declined", "actor", "Jane Doe"));

        assertEquals(1, posted.size());
        assertEquals(MarketplaceRequestStatus.ERROR, posted.get(0).status());
        assertTrue(posted.get(0).message().contains("Jane Doe"));
        assertEquals(MarketplaceRequestStatus.ERROR, reported);
    }

    /**
     * Scenario: Done issue without a recognised resolution is reported as failed
     *   Given request "id-1" is a registered MARKETPLACE_SUBSCRIBE without final result
     *   When Jira Automation sends an issue event for "id-1" with statusCategory "done" and an empty resolution
     *   Then an ERROR result stating the issue was closed without a recognised resolution is posted
     */
    @Test
    void whenDoneWithoutRecognisedResolutionThenFailed() {
        register(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, "id-1");
        outcome = Optional.of(new TicketOutcome(TicketOutcomeCause.FAILED, null));

        run("id-1", Map.of("statusCategory", "done", "resolution", ""));

        assertEquals(1, posted.size());
        assertEquals(MarketplaceRequestStatus.ERROR, posted.get(0).status());
        assertEquals(MarketplaceRequestStatus.ERROR, reported);
    }

    /**
     * Scenario: In-progress transition is ignored
     *   Given request "id-1" is a registered MARKETPLACE_SUBSCRIBE without final result
     *   When Jira Automation sends an issue event for "id-1" with statusCategory "indeterminate"
     *   Then no result is posted and the reason is "NON_TERMINAL_STATE"
     */
    @Test
    void whenNoRuleMatchesThenIgnoredAsNonTerminal() {
        register(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, "id-1");
        outcome = Optional.empty();

        run("id-1", Map.of("statusCategory", "indeterminate"));

        assertTrue(posted.isEmpty());
        assertEquals(TicketEventIgnoredReason.NON_TERMINAL_STATE, ignored);
        assertNull(reported);
        assertTrue(enrichedFrom.isEmpty());
    }

    /**
     * Scenario: Unknown correlation is ignored
     *   When Jira Automation sends an issue event for an identifier unknown to the Marketplace Service
     *   Then no result is posted and the reason is "UNKNOWN_REQUEST"
     */
    @Test
    void whenRequestUnknownThenIgnored() {
        registered = Optional.empty();

        run("unknown", Map.of("statusCategory", "done"));

        assertTrue(posted.isEmpty());
        assertEquals(TicketEventIgnoredReason.UNKNOWN_REQUEST, ignored);
    }

    /**
     * Scenario: a non-transition event never calls the marketplace
     *   When an issue event that is not a status transition arrives
     *   Then no Marketplace Service call is made and the reason is "NOT_A_STATUS_TRANSITION"
     */
    @Test
    void whenNotAStatusTransitionThenIgnoredWithoutMarketplaceCalls() {
        register(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, "id-1");

        run(new TicketEvent("id-1", Map.of("statusCategory", "done"), false));

        assertTrue(posted.isEmpty());
        assertTrue(marketplaceCalls.isEmpty());
        assertEquals(TicketEventIgnoredReason.NOT_A_STATUS_TRANSITION, ignored);
    }

    /**
     * Scenario: Last comment is read from Jira when the payload lacks it
     *   Given request "id-1" is a registered MARKETPLACE_SUBSCRIBE without final result
     *   And the native payload has no comment field
     *   And the latest comment of the issue is "Access granted in Snowflake"
     *   When the native done event for "id-1" is processed
     *   Then the GRANTED message contains "Access granted in Snowflake"
     */
    @Test
    void whenOutcomeFoundThenLastCommentFetchedForMessage() {
        register(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, "id-1");
        outcome = Optional.of(new TicketOutcome(TicketOutcomeCause.FULFILLED, null));

        run("id-1", Map.of("key", "ACC-1", "statusCategory", "done", "resolution", "Done"));

        assertEquals(1, enrichedFrom.size());
        assertEquals(1, posted.size());
        assertEquals(MarketplaceRequestStatus.GRANTED, posted.get(0).status());
        assertTrue(posted.get(0).message().contains("Access granted in Snowflake"));
    }

    /**
     * Scenario: Last comment failure does not block the result
     *   Given reading the issue comments fails with HTTP 500
     *   When the native done event for "id-1" is processed
     *   Then a GRANTED result is still posted without a comment
     * (the failure is absorbed by the details port, so the use case posts with the fields it gets back)
     */
    @Test
    void whenEnrichmentFailureAbsorbedThenResultStillReported() {
        register(MarketplaceOperation.MARKETPLACE_SUBSCRIBE, "id-1");
        outcome = Optional.of(new TicketOutcome(TicketOutcomeCause.FULFILLED, null));

        new CompleteTicket(
                new CompleteTicketCommand(new TicketEvent("id-1", Map.of("key", "ACC-1"))),
                presenter,
                marketplace,
                (operation, request) -> true,
                (operation, eventFields) -> outcome,
                (operation, request, found, status, eventFields) -> status + "|" + eventFields.getOrDefault("lastComment", ""),
                eventFields -> eventFields
        ).execute();

        assertEquals(1, posted.size());
        assertEquals(MarketplaceRequestStatus.GRANTED, posted.get(0).status());
        assertEquals("GRANTED|", posted.get(0).message());
    }
}
