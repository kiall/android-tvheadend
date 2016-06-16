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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.tasks.SyncEventsTask;

public class SyncJobService extends JobService {
    private static final String TAG = SyncJobService.class.getName();

    public static final long FULL_SYNC_FREQUENCY_MILLIS = 60 * 60 * 12 * 1000;  // twice daily
    public static final long OVERRIDE_DEADLINE_MILLIS = 1000;  // 1 second

    private static final Object mContextLock = new Object();
    private static Context mContext;
    private final SparseArray<SyncEventsTask> mTaskArray = new SparseArray<>();


    private AccountManager mAccountManager;
    private TVHClient mClient;

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (mContextLock) {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
        }

        mAccountManager = AccountManager.get(mContext);
        mClient = TVHClient.getInstance(mContext);

        setClientConnectionInfo();
    }

    private void setClientConnectionInfo() {
        Account[] accounts = mAccountManager.getAccountsByType(Constants.ACCOUNT_TYPE);

        // TODO: We should only every have one account.. Figure out how that works (or 1 account per
        //       hostname+port combo?)
        for (Account account : accounts) {
            mClient.setConnectionInfo(account);
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG, "onStartJob(" + params.getJobId() + ")");

        PersistableBundle extras = params.getExtras();
        String inputId = extras.getString(Constants.KEY_INPUT_ID);

        SyncEventsTask syncEventsTask = new SyncEventsTask(mContext, inputId) {
            @Override
            protected void onPostExecute(Boolean aBoolean) {
                finishEventsSync(params);
            }
        };

        synchronized (mTaskArray) {
            mTaskArray.put(params.getJobId(), syncEventsTask);
        }
        syncEventsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob(" + params.getJobId() + ")");
        synchronized (mTaskArray) {
            int jobId = params.getJobId();
            SyncEventsTask syncEventsTasks = mTaskArray.get(jobId);
            if (syncEventsTasks != null) {
                syncEventsTasks.cancel(true);
                mTaskArray.delete(params.getJobId());
            }
        }
        return false;
    }

    private void finishEventsSync(JobParameters jobParams) {
        jobFinished(jobParams, false);

        if (jobParams.getJobId() == Constants.REQUEST_SYNC_JOB_ID) {
            Intent intent = new Intent(Constants.ACTION_SYNC_STATUS_CHANGED);
            intent.putExtra(
                    Constants.KEY_INPUT_ID, jobParams.getExtras().getString(Constants.KEY_INPUT_ID));
            intent.putExtra(Constants.SYNC_STATUS, Constants.SYNC_FINISHED);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }
    }
}