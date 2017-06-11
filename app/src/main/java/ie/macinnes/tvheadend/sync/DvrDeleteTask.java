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

package ie.macinnes.tvheadend.sync;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseArray;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.tvheadend.TvContractUtils;

// TODO: Theres a race between this task, and the EpgSyncTask where any new recordings the
// EpgSyncTask adds will not exist until after we try and process them here. This will need some
// thought to fix....

public class DvrDeleteTask implements HtspMessage.Listener {
    private static final String TAG = DvrDeleteTask.class.getName();
    private static final Set<String> HANDLED_METHODS = new HashSet<>(Arrays.asList(new String[]{
            "dvrEntryAdd", "dvrEntryUpdate", "dvrEntryDelete",
    }));

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    protected HandlerThread mHandlerThread;
    protected Handler mHandler;

    private final Context mContext;
    private final HtspMessage.Dispatcher mDispatcher;
    private final ContentResolver mContentResolver;

    private final SparseArray<Uri> mRecordedProgramUriMap;

    public DvrDeleteTask(Context context, @NonNull HtspMessage.Dispatcher dispatcher) {
        mContext = context;
        mDispatcher = dispatcher;

        mContentResolver = mContext.getContentResolver();

        mHandlerThread = new HandlerThread("DvrDeleteTask Handler Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mRecordedProgramUriMap = new SparseArray<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mContentResolver.registerContentObserver(TvContract.RecordedPrograms.CONTENT_URI, true, mRecordedProgramContentObserver);
        }
    }

    public void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mContentResolver.unregisterContentObserver(mRecordedProgramContentObserver);
        }
    }

    // HtspMessage.Listener Methods
    @Override
    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public void onMessage(@NonNull HtspMessage message) {
        final String method = message.getString("method");

        if (HANDLED_METHODS.contains(method)) {
            switch (method) {
                case "dvrEntryAdd":
                case "dvrEntryUpdate":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        handleDvrEntryAddUpdate(message);
                    }
                    break;
                case "dvrEntryDelete":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        handleDvrEntryDelete(message);
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown message method: " + method);
            }
        }
    }

    // Misc Internal Methods
    private static final String DVR_ENTRY_ID_KEY = "id";

    private void handleDvrEntryAddUpdate(@NonNull HtspMessage message) {
        final int dvrEntryId = message.getInteger(DVR_ENTRY_ID_KEY);

        if (mRecordedProgramUriMap.indexOfKey(dvrEntryId) >= 0) {
            // We already have this program in our map, lets save some CPU cycles and do nothing
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Uri recordedProgramUri = TvContractUtils.getRecordedProgramUri(mContext, dvrEntryId);

            mRecordedProgramUriMap.put(dvrEntryId, recordedProgramUri);
        }
    }

    private void handleDvrEntryDelete(@NonNull HtspMessage message) {
        final int dvrEntryId = message.getInteger(DVR_ENTRY_ID_KEY);
        mRecordedProgramUriMap.remove(dvrEntryId);
    }

    private final ContentObserver mRecordedProgramContentObserver = new ContentObserver(mMainThreadHandler) {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onChange(boolean selfChange, @Nullable final Uri recordedProgramUri) {
            if (recordedProgramUri == null) {
                // The RecordedPrograms "folder" changed, ignore
                return;
            }

            String[] projection = {TvContract.RecordedPrograms._ID, TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA};

            // TODO: Handle when more than 1, or 0 results come back
            try (Cursor cursor = mContentResolver.query(recordedProgramUri, projection, null,null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    // If we find a match, the entry still exists - bail.
                    return;
                }
            }

            // If we get here, it means the recorded program was deleted. This is all pretty nasty.
            int dvrEntryId = -1;

            for (int i = 0; i < mRecordedProgramUriMap.size(); i++) {
                int key = mRecordedProgramUriMap.keyAt(i);
                Uri obj = mRecordedProgramUriMap.get(key);

                if (obj.equals(recordedProgramUri)) {
                    dvrEntryId = key;
                }
            }

            if (dvrEntryId >= 0) {
                HtspMessage deleteDvrEntry = new HtspMessage();
                deleteDvrEntry.put("method", "deleteDvrEntry");
                deleteDvrEntry.put("id", dvrEntryId);

                try {
                    mDispatcher.sendMessage(deleteDvrEntry);
                } catch (HtspNotConnectedException e) {
                    Log.e(TAG, "Failed to send deleteDvrEntry - not connected", e);
                }
            }
        }
    };
}
