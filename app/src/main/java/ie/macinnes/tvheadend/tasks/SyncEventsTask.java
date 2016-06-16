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

package ie.macinnes.tvheadend.tasks;


import android.content.Context;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.util.concurrent.CountDownLatch;

import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.TvContractUtils;

public class SyncEventsTask extends AsyncTask<Void, Void, Boolean> {
    public static final String TAG = SyncEventsTask.class.getSimpleName();

    private final Context mContext;

    private final TVHClient mClient;

    private CountDownLatch mCountDownLatch;

    protected SyncEventsTask(Context context) {
        mContext = context;
        mClient = TVHClient.getInstance(context);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Log.d(TAG, "Starting SyncEventsTask");
        if (isCancelled()) {
            return false;
        }

        ChannelList channelList = findChannels();

        mCountDownLatch = new CountDownLatch(channelList.size());

        for (Channel channel : channelList) {
            updateChannel(channel);
        }

        try {
            mCountDownLatch.await();
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while awaiting all channels to sync events: " + e.getLocalizedMessage());
            return false;
        }

        return true;
    }

    private ChannelList findChannels() {
        Log.d(TAG, "Fetching channel list from TV DB");
        // Select only a few columns
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA
        };

        // Gather and return ChannelList
        return TvContractUtils.getChannels(mContext, projection);
    }

    private void updateChannel(final Channel channel) {

        // Prepare the SyncChannelEventsTask
        final SyncChannelEventsTask syncChannelEventsTask = new SyncChannelEventsTask(mContext, channel) {
            @Override
            protected void onPostExecute(Boolean completed) {
                mCountDownLatch.countDown();
            }
        };

        Response.Listener<TVHClient.EventList> listener = new Response.Listener<TVHClient.EventList>() {
            @Override
            public void onResponse(TVHClient.EventList eventList) {
                syncChannelEventsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, eventList);
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Failed to fetch events for channel " + channel.toString());
                mCountDownLatch.countDown();
            }
        };

        Log.d(TAG, "Fetching events for channel " + channel.toString());
        mClient.getEventGrid(listener, errorListener, channel.getInternalProviderData().getUuid());
    }
}
