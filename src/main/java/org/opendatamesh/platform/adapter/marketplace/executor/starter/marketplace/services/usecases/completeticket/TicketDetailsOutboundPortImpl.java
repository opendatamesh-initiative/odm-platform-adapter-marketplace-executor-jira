package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core.JiraIssueService;

import java.util.Map;

class TicketDetailsOutboundPortImpl implements TicketDetailsOutboundPort {

    private final JiraIssueService issueService;

    TicketDetailsOutboundPortImpl(JiraIssueService issueService) {
        this.issueService = issueService;
    }

    @Override
    public Map<String, String> withLastComment(Map<String, String> fields) {
        return issueService.withLastComment(fields);
    }
}
