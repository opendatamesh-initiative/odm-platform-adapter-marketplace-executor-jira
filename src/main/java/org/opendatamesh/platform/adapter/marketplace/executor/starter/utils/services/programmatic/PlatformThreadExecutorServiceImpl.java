package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services.programmatic;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class PlatformThreadExecutorServiceImpl implements PlatformThreadExecutorService {

    private final ExecutorService executor;

    public PlatformThreadExecutorServiceImpl(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        executor.execute(() -> runWithContext(runnable, context));
    }

    private void runWithContext(Runnable runnable, Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        }
        try {
            runnable.run();
        } finally {
            MDC.clear();
        }
    }
}
