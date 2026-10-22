package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services.programmatic;

/**
 * Runs work in the background on the Quarkus worker pool (platform threads).
 * Use it for CPU-heavy work, or code that blocks inside {@code synchronized} or native calls.
 * For blocking I/O, use {@link AsyncExecutorService}.
 * The pool is shared with blocking HTTP endpoints. The caller's MDC (correlation id) is copied into the task.
 * Configured with {@code quarkus.thread-pool.*}.
 */
public interface PlatformThreadExecutorService {

    void execute(Runnable runnable);
}
