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
import android.os.AsyncTask;
import android.util.Log;

import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.TvContractUtils;

public class SyncChannelsTask extends AsyncTask<TVHClient.ChannelList, Void, Boolean> {
    public static final String TAG = SyncChannelsTask.class.getSimpleName();

    private final Context mContext;

    private final TVHClient mClient;
    private boolean complete;

    protected SyncChannelsTask(Context context) {
        mContext = context;

        mClient = TVHClient.getInstance(context);
    }

    @Override
    protected Boolean doInBackground(TVHClient.ChannelList... channelLists) {
        Log.d(TAG, "Starting SyncChannelsTask");
        if (isCancelled()) {
            return false;
        }

        for (TVHClient.ChannelList channelList : channelLists) {
            if (isCancelled()) {
                return false;
            }

            // Update the channels DB
            TvContractUtils.updateChannels(
                    mContext,

                    ChannelList.fromClientChannelList(channelList));
        }

        return true;
    }
}
