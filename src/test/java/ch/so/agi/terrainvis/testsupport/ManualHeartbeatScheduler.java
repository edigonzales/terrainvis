package ch.so.agi.terrainvis.testsupport;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import ch.so.agi.terrainvis.util.HeartbeatScheduler;

public final class ManualHeartbeatScheduler implements HeartbeatScheduler {
    private final AtomicReference<Runnable> scheduledTask = new AtomicReference<>();

    @Override
    public Cancellable scheduleAtFixedRate(Duration initialDelay, Duration interval, Runnable task) {
        scheduledTask.set(task);
        return () -> scheduledTask.set(null);
    }

    public void trigger() {
        Runnable task = scheduledTask.get();
        if (task != null) {
            task.run();
        }
    }
}
