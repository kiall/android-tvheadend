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
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ProgramList;
import ie.macinnes.tvheadend.TvContractUtils;

public class SyncProgramsTask extends AsyncTask<TVHClient.EventList, Void, Boolean> {
    public static final String TAG = SyncProgramsTask.class.getSimpleName();

    private final Context mContext;
    private final Channel mChannel;

    protected SyncProgramsTask(Context context, Channel channel) {
        mContext = context;
        mChannel = channel;
    }

    @Override
    protected Boolean doInBackground(TVHClient.EventList... eventLists) {
        Log.d(TAG, "Starting SyncProgramsTask for channel: " + mChannel.toString());

        if (isCancelled()) {
            return false;
        }

        for (TVHClient.EventList eventList : eventLists) {
            if (isCancelled()) {
                return false;
            }

            // Update the events in DB
            TvContractUtils.updateEvents(
                    mContext,
                    mChannel,
                    ProgramList.fromClientEventList(eventList, mChannel.getId())
            );
        }

        return true;
    }
}
