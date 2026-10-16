package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain;

import java.util.List;

public record Provider(String dataProductFqn, List<String> dataProductPortsFqn) {
}
