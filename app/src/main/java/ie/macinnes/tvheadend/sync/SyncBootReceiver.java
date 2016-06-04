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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

import ie.macinnes.tvheadend.Constants;

public class SyncBootReceiver extends BroadcastReceiver {
    public SyncBootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        // If there are no pending jobs, create a sync job and schedule it.
        List<JobInfo> pendingJobs = jobScheduler.getAllPendingJobs();

        if (pendingJobs.isEmpty()) {
            String inputId = context.getSharedPreferences(Constants.PREFERENCE_TVHEADEND,
                    Context.MODE_PRIVATE).getString(Constants.KEY_INPUT_ID, null);

            if (inputId != null) {
                // Set up periodic sync only when input has set up.
                SyncUtils.setUpPeriodicSync(context, inputId);
            }

            return;
        }

        // But on L/L-MR1, reschedule the pending jobs.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            for (JobInfo job : pendingJobs) {
                if (job.isPersisted()) {
                    jobScheduler.schedule(job);
                }
            }
        }
    }
}
