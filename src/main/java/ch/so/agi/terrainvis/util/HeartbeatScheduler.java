package ch.so.agi.terrainvis.util;

import java.time.Duration;

public interface HeartbeatScheduler extends AutoCloseable {
    Cancellable scheduleAtFixedRate(Duration initialDelay, Duration interval, Runnable task);

    @Override
    default void close() {
    }

    interface Cancellable {
        void cancel();
    }
}
