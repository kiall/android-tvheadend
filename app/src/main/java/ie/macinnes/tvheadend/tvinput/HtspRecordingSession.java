/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ie.macinnes.tvheadend.tvinput;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.TvContractUtils;


@RequiresApi(api = Build.VERSION_CODES.N)
public class HtspRecordingSession extends TvInputService.RecordingSession {
    private static final String TAG = HtspRecordingSession.class.getName();
    private static final int INVALID_DVR_ENTRY_ID = -1;
    private static final AtomicInteger sSessionCounter = new AtomicInteger();

    private final Context mContext;
    private final SimpleHtspConnection mConnection;
    private final int mSessionNumber;
    private final Handler mHandler;
    private final SharedPreferences mSharedPreferences;

    private Uri mChannelUri;
    private Uri mProgramUri;
    private int mDvrEntryId = INVALID_DVR_ENTRY_ID;

    public HtspRecordingSession(Context context, SimpleHtspConnection connection) {
        super(context);

        mContext = context;
        mConnection = connection;
        mSessionNumber = sSessionCounter.getAndIncrement();
        mHandler = new Handler();

        mSharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        Log.d(TAG, "HtspRecordingSession created (" + mSessionNumber + ")");
    }

    @Override
    public void onTune(Uri channelUri) {
        Log.d(TAG, "RecordingSession onTune (" + mSessionNumber + ")");

        mChannelUri = channelUri;

        // I'm not sure we really need to do anything here?
        notifyTuned(channelUri);
    }

    @Override
    public void onStartRecording(@Nullable Uri programUri) {
        Log.d(TAG, "RecordingSession onStartRecording (" + mSessionNumber + ")");

        mProgramUri = programUri;

        if (mProgramUri == null || mChannelUri == null) {
            Log.e(TAG, "Failed to start recording, programUri or channelUri is null");
            return;
        }

        Integer eventId = TvContractUtils.getTvhEventIdFromProgramUri(mContext, mProgramUri);
        Integer channelId = TvContractUtils.getTvhChannelIdFromChannelUri(mContext, mChannelUri);

        if (eventId == null || channelId == null) {
            Log.e(TAG, "Failed to start recording, eventId or channelId is null");
            return;
        }

        HtspMessage addDvrEntry = new HtspMessage();
        addDvrEntry.put("method", "addDvrEntry");
        addDvrEntry.put("eventId", eventId);
        addDvrEntry.put("channelId", channelId);

        HtspMessage addDvrEntryResponse;

        try {
            addDvrEntryResponse = mConnection.sendMessage(addDvrEntry, 5000);
        } catch (HtspNotConnectedException e) {
            Log.e(TAG, "Failed to start recording, HTSP not connected", e);
            notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            return;
        }

        boolean success = addDvrEntryResponse.getBoolean("success");

        if (success) {
            mDvrEntryId = addDvrEntryResponse.getInteger("id");
            Log.i(TAG, "DVR Entry created with ID: " + mDvrEntryId);
        } else {
            String error = addDvrEntryResponse.getString("error", "Unknown error");
            Log.e(TAG, "Failed to create DVR Entry: " + error);
            notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
        }
    }

    @Override
    public void onStopRecording() {
        Log.d(TAG, "RecordingSession onStopRecording (" + mSessionNumber + ")");

        if (mDvrEntryId == INVALID_DVR_ENTRY_ID) {
            Log.e(TAG, "Failed to stop recording, no known DvrEntryId");
            return;
        }

        HtspMessage cancelDvrEntry = new HtspMessage();
        cancelDvrEntry.put("method", "cancelDvrEntry");
        cancelDvrEntry.put("id", mDvrEntryId);

        try {
            mConnection.sendMessage(cancelDvrEntry);
        } catch (HtspNotConnectedException e) {
            Log.e(TAG, "Failed to stop recording, HTSP not connected", e);
        }

        notifyRecordingStopped(null);
    }

    @Override
    public void onRelease() {
        Log.d(TAG, "RecordingSession onRelease (" + mSessionNumber + ")");
    }
}
