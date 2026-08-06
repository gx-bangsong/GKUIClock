/*
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.alarms;

import static com.best.deskclock.DeskClockApplication.getDefaultSharedPreferences;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.best.deskclock.data.SettingsDAO;
import com.best.deskclock.utils.LogUtils;

/**
 * Keeps calendar synchronization observable even after Android kills the Clock process.
 *
 * <p>Android 7+ uses a CalendarProvider content-URI trigger. Android 6, where URI-triggered jobs
 * are unavailable, uses the platform's minimum periodic interval as a fallback.</p>
 */
public final class ShiftCalendarSyncJobService extends JobService
        implements ShiftCalendarManager.SyncListener {

    private static final int JOB_ID = 0x53484654; // "SHFT"
    private static final long FALLBACK_INTERVAL_MILLIS = 15L * 60L * 1000L;

    private JobParameters mParameters;
    private long mStartedAt;

    public static void schedule(Context context) {
        schedule(context, false);
    }

    private static void schedule(Context context, boolean replaceExisting) {
        final Context appContext = context.getApplicationContext();
        final JobScheduler scheduler = appContext.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }

        if (!SettingsDAO.isCalendarShiftSyncEnabled(
                getDefaultSharedPreferences(appContext))
                || ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            scheduler.cancel(JOB_ID);
            return;
        }

        if (!replaceExisting) {
            for (JobInfo pendingJob : scheduler.getAllPendingJobs()) {
                if (pendingJob.getId() == JOB_ID) {
                    return;
                }
            }
        }

        final JobInfo.Builder builder = new JobInfo.Builder(JOB_ID,
                new ComponentName(appContext, ShiftCalendarSyncJobService.class));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.addTriggerContentUri(new JobInfo.TriggerContentUri(
                    CalendarContract.Events.CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
            builder.setTriggerContentUpdateDelay(2_000L);
            builder.setTriggerContentMaxDelay(30_000L);
        } else {
            builder.setPeriodic(FALLBACK_INTERVAL_MILLIS);
            builder.setPersisted(true);
        }

        final int result = scheduler.schedule(builder.build());
        if (result != JobScheduler.RESULT_SUCCESS) {
            LogUtils.w("Unable to schedule calendar shift synchronization job");
        }
    }

    public static void cancel(Context context) {
        final JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mParameters = params;
        mStartedAt = System.currentTimeMillis();
        final ShiftCalendarManager manager = ShiftCalendarManager.getInstance(this);
        manager.addListener(this);
        manager.requestImmediateSync();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        ShiftCalendarManager.getInstance(this).removeListener(this);
        mParameters = null;
        return SettingsDAO.isCalendarShiftSyncEnabled(getDefaultSharedPreferences(this))
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onShiftSyncChanged() {
        final ShiftCalendarManager manager = ShiftCalendarManager.getInstance(this);
        if (mParameters == null || manager.isSyncInProgress()
                || manager.getLastSyncTime() < mStartedAt) {
            return;
        }

        final JobParameters completed = mParameters;
        mParameters = null;
        manager.removeListener(this);
        jobFinished(completed, false);

        // URI-triggered jobs are one-shot. Register the next trigger after this run completes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            schedule(this, true);
        }
    }
}
