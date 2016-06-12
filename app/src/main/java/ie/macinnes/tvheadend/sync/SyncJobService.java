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
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.SparseArray;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.model.ProgramList;
import ie.macinnes.tvheadend.utils.TvContractUtils;

public class SyncJobService extends JobService {
    private static final String TAG = SyncJobService.class.getName();

    public static final long FULL_SYNC_FREQUENCY_MILLIS = 60 * 60 * 24 * 1000;  // daily
    public static final long OVERRIDE_DEADLINE_MILLIS = 1000;  // 1 second

    private static final Object mContextLock = new Object();
    private static Context mContext;
    private final SparseArray<EpgSyncTask> mTaskArray = new SparseArray<>();


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
        Account[] accounts = mAccountManager.getAccountsByType("ie.macinnes.tvheadend");

        // TODO: We should only every have one account.. Figure out how that works (or 1 account per
        //       hostname+port combo?)
        for (Account account : accounts) {
            mClient.setConnectionInfo(account);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob(" + params.getJobId() + ")");

        EpgSyncTask epgSyncTask = new EpgSyncTask(params);
        synchronized (mTaskArray) {
            mTaskArray.put(params.getJobId(), epgSyncTask);
        }
        epgSyncTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob(" + params.getJobId() + ")");
        synchronized (mTaskArray) {
            int jobId = params.getJobId();
            EpgSyncTask epgSyncTask = mTaskArray.get(jobId);
            if (epgSyncTask != null) {
                epgSyncTask.cancel(true);
                mTaskArray.delete(params.getJobId());
            }
        }
        return false;
    }

    private class EpgSyncTask extends AsyncTask<Void, Void, Void> {
        private final JobParameters params;

        private EpgSyncTask(JobParameters params) {
            this.params = params;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (isCancelled()) {
                return null;
            }

            PersistableBundle extras = params.getExtras();
            String inputId = extras.getString(Constants.KEY_INPUT_ID);

            if (inputId == null) {
                return null;
            }

            String[] projection = {
                    TvContract.Channels._ID,
                    TvContract.Channels.COLUMN_DISPLAY_NAME,
                    TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                    TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA
            };

            // Gather Channel List
            ChannelList channelList = TvContractUtils.getChannels(mContext, inputId, projection);

            // Fetch EPG for each channel
            for (Channel channel : channelList) {
                findEvents(channel);
            }

            return null;
        }

        private void findEvents(final Channel channel) {
            Response.Listener<TVHClient.EventList> listener = new Response.Listener<TVHClient.EventList>() {

                @Override
                public void onResponse(TVHClient.EventList eventList) {
                    TvContractUtils.updateEvents(
                            mContext,
                            channel,
                            ProgramList.fromClientEventList(eventList, channel.getId())
                    );

                    mTaskArray.delete(params.getJobId());
                    jobFinished(params, false);
                }
            };

            Response.ErrorListener errorListener = new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO: Do Something Useful Here
                }
            };

            mClient.getEventGrid(listener, errorListener, channel.getInternalProviderData().getUuid());
        }
    }
}