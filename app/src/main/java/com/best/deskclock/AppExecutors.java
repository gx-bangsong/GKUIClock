// SPDX-License-Identifier: GPL-3.0-only

package com.best.deskclock;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide executors used by the application.
 *
 * <p>Alarm mutations are deliberately serialized to preserve database and scheduling order. Other
 * disk work uses a separate small pool so ringtone scans and file operations cannot block alarm
 * state changes.</p>
 */
public final class AppExecutors {

    private static final ExecutorService ALARM_IO =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("alarm-io"));

    private static final ExecutorService DISK_IO =
            Executors.newFixedThreadPool(2, new NamedThreadFactory("disk-io"));

    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    /** Returns the single executor that owns alarm and alarm-instance mutations. */
    public static ExecutorService getAlarmIO() {
        return ALARM_IO;
    }

    /** Returns the shared pool for independent blocking disk and provider work. */
    public static ExecutorService getDiskIO() {
        return DISK_IO;
    }

    /** Returns the process-wide main-thread handler. */
    public static Handler getMainThread() {
        return MAIN_THREAD;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String mPrefix;
        private final AtomicInteger mCount = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            mPrefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, mPrefix + '-' + mCount.incrementAndGet());
        }
    }
}
