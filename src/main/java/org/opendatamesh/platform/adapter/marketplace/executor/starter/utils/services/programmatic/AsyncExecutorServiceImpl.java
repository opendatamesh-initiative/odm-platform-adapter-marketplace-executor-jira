package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services.programmatic;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class AsyncExecutorServiceImpl implements AsyncExecutorService {

    private final ExecutorService executor;

    public AsyncExecutorServiceImpl() {
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("async-executor");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void execute(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        executor.execute(() -> runWithContext(runnable, context));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
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
