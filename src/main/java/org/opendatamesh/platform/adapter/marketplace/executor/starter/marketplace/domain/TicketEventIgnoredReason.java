package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

public enum TicketEventIgnoredReason {
    NOT_A_STATUS_TRANSITION,
    MISSING_CORRELATION,
    UNKNOWN_REQUEST,
    NOT_HANDLED_BY_EXECUTOR,
    FINAL_RESULT_ALREADY_REPORTED,
    NON_TERMINAL_STATE
}
