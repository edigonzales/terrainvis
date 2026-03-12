package ch.so.agi.terrainvis.util;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class ScheduledHeartbeatScheduler implements HeartbeatScheduler {
    private final ScheduledExecutorService executorService;

    public ScheduledHeartbeatScheduler(String threadName) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
        this.executorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Override
    public Cancellable scheduleAtFixedRate(Duration initialDelay, Duration interval, Runnable task) {
        var future = executorService.scheduleAtFixedRate(
                task,
                initialDelay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
