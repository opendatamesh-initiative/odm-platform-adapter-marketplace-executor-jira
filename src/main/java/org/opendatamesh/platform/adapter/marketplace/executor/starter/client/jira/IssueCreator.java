package org.opendatamesh.platform.adapter.marketplace.executor.starter.client.jira;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.IssueInterfaceType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.IssueReference;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequest;

import java.util.Map;

public interface IssueCreator {

    IssueInterfaceType type();

    IssueReference create(JiraConfig.IssueProfile profile, Map<String, Object> templateData, MarketplaceRequest request);
}
