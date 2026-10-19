package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import java.util.Map;

public interface TicketDetailsOutboundPort {

    Map<String, String> withLastComment(Map<String, String> fields);
}
