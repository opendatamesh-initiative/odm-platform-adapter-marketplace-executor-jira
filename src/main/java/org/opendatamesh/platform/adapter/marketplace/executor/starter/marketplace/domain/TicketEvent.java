package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

import java.util.Map;

public record TicketEvent(String correlationId, Map<String, String> fields, boolean statusTransition) {

    public TicketEvent {
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }

    // the Automation rule fires on transitions only
    public TicketEvent(String correlationId, Map<String, String> fields) {
        this(correlationId, fields, true);
    }
}
