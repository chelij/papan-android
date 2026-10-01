package com.papan.share;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeliveryJob extends JobService {
    private AtomicBoolean cancelled;
    @Override public boolean onStartJob(JobParameters parameters) {
        AtomicBoolean stop = new AtomicBoolean(false); cancelled = stop;
        new Thread(() -> {
            boolean pending = true;
            try { pending = Delivery.sync(this, stop); } catch (Exception ignored) { /* Preserve the queue for the next foreground attempt. */ }
            if (!stop.get()) jobFinished(parameters, pending);
        }, "papan-delivery").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters) { if (cancelled != null) cancelled.set(true); return true; }
}
