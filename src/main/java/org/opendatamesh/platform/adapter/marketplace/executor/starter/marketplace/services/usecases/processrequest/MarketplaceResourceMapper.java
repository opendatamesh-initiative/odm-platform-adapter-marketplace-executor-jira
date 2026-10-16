package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.processrequest;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Provider;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.resources.ProviderRes;

final class MarketplaceResourceMapper {

    private MarketplaceResourceMapper() {
    }

    static ProviderRes toProviderRes(Provider provider) {
        if (provider == null) {
            return null;
        }
        ProviderRes res = new ProviderRes();
        res.setDataProductFqn(provider.dataProductFqn());
        res.setDataProductPortsFqn(provider.dataProductPortsFqn());
        return res;
    }
}
