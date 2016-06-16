/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package ie.macinnes.tvheadend.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.tasks.SyncProgramsTask;


public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = SyncAdapter.class.getName();

    private final Context mContext;
    private final ContentResolver mContentResolver;

    private final TVHClient mClient;

    private boolean mIsCancelled = false;
    private ArrayList<AsyncTask> mPendingTasks = new ArrayList<AsyncTask>();

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int INITIAL_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2;
    private static final int KEEP_ALIVE_TIME = 1;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "EpgSyncTask #" + mCount.getAndIncrement());
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(128);

    private static final Executor sExecutor
            = new ThreadPoolExecutor(INITIAL_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);

        mContext = context;
        mContentResolver = context.getContentResolver();

        mClient = TVHClient.getInstance(context);
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);

        mContext = context;
        mContentResolver = context.getContentResolver();

        mClient = TVHClient.getInstance(context);
    }

    @Override
    public void onSyncCanceled() {
        Log.d(TAG, "Sync cancellation requested");
        mIsCancelled = true;

        Log.d(TAG, "Cancelling " + mPendingTasks.size() + " pending tasks");

        while (mPendingTasks.size() != 0) {
            AsyncTask asyncTask = mPendingTasks.get(0);
            asyncTask.cancel(true);
            mPendingTasks.remove(asyncTask);
        }
    }

    public boolean isCancelled() {
        return mIsCancelled;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG, "Starting sync for account: " + account.toString());

        // TODO: The TVHClient class needs to support multiple accounts, this is racy as other
        // processes may be using this singleton with different creds.
        mClient.setConnectionInfo(account);

        if (isCancelled()) {
            Log.d(TAG, "Sync cancelled");
            return;
        }

        // Sync Channels
        if (!syncChannels()) {
            return;
        }

        if (isCancelled()) {
            Log.d(TAG, "Sync cancelled");
            return;
        }

        // Sync Programs
        if (!syncPrograms()) {
            return;
        }

        // Wait for all tasks to finish
        Log.d(TAG, "Completed sync for account: " + account.toString());
    }

    private boolean syncChannels() {
        Log.d(TAG, "Starting channel sync");

        TVHClient.ChannelList channelList;

        try {
            channelList = mClient.getChannelGrid();
        } catch (InterruptedException|ExecutionException e) {
            // Something went wrong
            Log.w(TAG, "Failed to fetch channel list from server: " + e.getLocalizedMessage(), e);
            return false;
        } catch (TimeoutException e) {
            // Request timed out
            Log.w(TAG, "Failed to fetch channel list from server, timed out");
            return false;
        }

        // Update the channels DB (Also does channel logos)
        TvContractUtils.updateChannels(
                mContext,
                ChannelList.fromClientChannelList(channelList));

        Log.d(TAG, "Completed channel sync");
        return true;
    }

    private boolean syncPrograms() {
        Log.d(TAG, "Starting program sync");

        // Gather the list of channels from TvProvider
        // Select only a few columns
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA
        };

        // Fetch the ChannelList
        ChannelList channelList = TvContractUtils.getChannels(mContext, projection);

        // Update the EPG for each channel
        final CountDownLatch countDownLatch = new CountDownLatch(channelList.size());

        for (Channel channel : channelList) {
            updateChannelPrograms(countDownLatch, channel);
        }

        // Wait for all tasks to finish
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while awaiting program sync to complete: " + e.getLocalizedMessage());
            return false;
        }

        Log.d(TAG, "Completed program sync");
        return true;
    }

    private boolean updateChannelPrograms(final CountDownLatch countDownLatch, final Channel channel) {
        Log.d(TAG, "Fetching events for channel " + channel.toString());

        TVHClient.EventList eventList;

        try {
            eventList = mClient.getEventGrid(channel.getInternalProviderData().getUuid());
        } catch (InterruptedException|ExecutionException e) {
            // Something went wrong
            Log.w(TAG, "Failed to fetch event list from server: " + e.getLocalizedMessage(), e);
            return false;
        } catch (TimeoutException e) {
            // Request timed out
            Log.w(TAG, "Failed to fetch event  list from server, timed out");
            return false;
        }

        // Prepare the SyncProgramsTask
        final SyncProgramsTask syncProgramsTask = new SyncProgramsTask(mContext, channel) {
            @Override
            protected void onPostExecute(Boolean completed) {
                mPendingTasks.remove(this);
                countDownLatch.countDown();
            }

            @Override
            protected void onCancelled() {
                mPendingTasks.remove(this);
                countDownLatch.countDown();
            }
        };

        mPendingTasks.add(syncProgramsTask.executeOnExecutor(sExecutor, eventList));

        return true;
    }
}
