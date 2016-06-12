/* Copyright 2016 Kiall Mac Innes <kiall@macinnes.ie>

Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain
a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations
under the License.
*/
package ie.macinnes.tvheadend.sync;


import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v4.content.LocalBroadcastManager;

import ie.macinnes.tvheadend.Constants;

public class SyncUtils {

    private static void scheduleJob(Context context, JobInfo job) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    public static void setUpPeriodicSync(Context context, String inputId) {
        PersistableBundle pBundle = new PersistableBundle();

        pBundle.putString(Constants.KEY_INPUT_ID, inputId);

        JobInfo.Builder builder = new JobInfo.Builder(Constants.PERIODIC_SYNC_JOB_ID,
                new ComponentName(context, SyncJobService.class));

        JobInfo jobInfo = builder
                .setExtras(pBundle)
                .setPeriodic(SyncJobService.FULL_SYNC_FREQUENCY_MILLIS)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();

        scheduleJob(context, jobInfo);
    }

    public static void requestSync(Context context, String inputId) {
        PersistableBundle pBundle = new PersistableBundle();

        pBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        pBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        pBundle.putString(Constants.KEY_INPUT_ID, inputId);

        JobInfo.Builder builder = new JobInfo.Builder(Constants.REQUEST_SYNC_JOB_ID,
                new ComponentName(context, SyncJobService.class));

        JobInfo jobInfo = builder
                .setExtras(pBundle)
                .setOverrideDeadline(SyncJobService.OVERRIDE_DEADLINE_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);

        Intent intent = new Intent(Constants.ACTION_SYNC_STATUS_CHANGED);
        intent.putExtra(Constants.KEY_INPUT_ID, inputId);
        intent.putExtra(Constants.SYNC_STATUS, Constants.SYNC_STARTED);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void cancelAll(Context context) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }
}
