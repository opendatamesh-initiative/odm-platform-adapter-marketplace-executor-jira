package org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.services.usecases.completeticket;

import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.MarketplaceRequestStatus;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.TicketEventIgnoredReason;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCasePresenter;

public interface CompleteTicketPresenter extends UseCasePresenter {

    void presentReported(MarketplaceRequestStatus status);

    void presentIgnored(TicketEventIgnoredReason reason);
}
