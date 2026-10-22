package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

public record IssueReference(String key, String id, String link) {

    public IssueReference {
        key = key == null ? "" : key;
        id = id == null ? "" : id;
        link = link == null ? "" : link;
    }
}
