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
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.htsp.MessageListener;
import ie.macinnes.htsp.ResponseMessage;
import ie.macinnes.htsp.messages.BaseChannelResponse;
import ie.macinnes.htsp.messages.BaseEventResponse;
import ie.macinnes.htsp.messages.EnableAsyncMetadataRequest;
import ie.macinnes.htsp.messages.InitialSyncCompletedResponse;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.tasks.SyncLogosTask;

class EpgSyncTask extends MessageListener {
    // channelId and eventId in this are, ehh, confusing. We have the TVH channel/event IDs, and the
    // Android TV channel/program IDs.
    private static final String TAG = EpgSyncTask.class.getName();

    protected Context mContext;
    protected Account mAccount;
    protected Runnable mInitialSyncCompleteCallback;

    protected ContentResolver mContentResolver;

    protected SparseArray<Uri> mChannelUriMap;
    protected SparseArray<Uri> mProgramUriMap;

    protected Set<Integer> mSeenChannels;
    protected Set<Integer> mSeenPrograms;

    protected ArrayList<ContentProviderOperation> mPendingProgramOps = new ArrayList<>();

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
            new LinkedBlockingQueue<Runnable>();

    private static final Executor sExecutor = new ThreadPoolExecutor(INITIAL_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

    public EpgSyncTask(Context context, Account account) {
        mContext = context;
        mAccount = account;

        mContentResolver = mContext.getContentResolver();
        mChannelUriMap = buildChannelUriMap();
        mProgramUriMap = buildProgramUriMap();

        mSeenChannels = new HashSet<>();
        mSeenPrograms = new HashSet<>();
    }

    public void enableAsyncMetadata(Runnable initialSyncCompleteCallback) {
        mInitialSyncCompleteCallback = initialSyncCompleteCallback;

        long unixTime = System.currentTimeMillis() / 1000L;

        EnableAsyncMetadataRequest enableAsyncMetadataRequest = new EnableAsyncMetadataRequest();
        enableAsyncMetadataRequest.setEpgMaxTime(unixTime + 6 * 60 * 60);
        enableAsyncMetadataRequest.setEpg(true);

        mConnection.sendMessage(enableAsyncMetadataRequest);
    }

    @Override
    public void onMessage(ResponseMessage message) {
        Log.v(TAG, "Received Message: " + message.getClass() + " / " + message.toString());

        if (message instanceof InitialSyncCompletedResponse) {
            flushPendingProgramOps();
            deleteChannels();
            deletePrograms();

            Log.i(TAG, "Initial sync completed");

            if (mInitialSyncCompleteCallback != null) {
                mInitialSyncCompleteCallback.run();
            }
        } else if (message instanceof BaseChannelResponse) {
            handleChannel((BaseChannelResponse) message);
        } else if (message instanceof BaseEventResponse) {
            handleEvent((BaseEventResponse) message);
        }
    }

    private void handleChannel(BaseChannelResponse message) {
        Log.d(TAG, "Handling channel message for ID: " + message.getChannelId());

        Uri channelUri = getChannelUri(message.getChannelId());
        ContentValues values = message.toContentValues(TvContractUtils.getInputId(), mAccount.name);

        if (channelUri == null) {
            // Insert the channel
            Log.d(TAG, "Insert channel " + message.getChannelName());
            channelUri = mContentResolver.insert(TvContract.Channels.CONTENT_URI, values);
            mChannelUriMap.append(message.getChannelId(), channelUri);
        } else {
            // Update the channel
            Log.d(TAG, "Update channel " + message.getChannelId());
            mContentResolver.update(channelUri, values, null, null);
        }

        if (message.getChannelIcon() != null) {
            fetchChannelLogo(channelUri, message);
        }

        mSeenChannels.add(message.getChannelId());
    }

    private void fetchChannelLogo(Uri channelUri, BaseChannelResponse message) {
        Uri channelLogoUri = TvContract.buildChannelLogoUri(channelUri);

        Map<Uri, String> logos = new HashMap<>();
        logos.put(channelLogoUri, message.getChannelIcon());

        SyncLogosTask syncLogosTask = new SyncLogosTask(mContext);
        syncLogosTask.executeOnExecutor(sExecutor, logos);
    }

    private void handleEvent(BaseEventResponse message) {
        Log.d(TAG, "Handling event message for ID: " + message.getEventId());

        if (message.getChannelId() == BaseEventResponse.INVALID_INT_VALUE) {
            Log.e(TAG, "Discarding event message for unknown channel, event ID " + message.getEventId());
            return;
        }

        Long channelId = getChannelId(message.getChannelId());

        if (channelId == null) {
            Log.w(TAG, "Failed to handle event message for ID: " + message.getEventId() + ", unknown channel.");
        }

        Uri programUri = getProgramUri(message.getChannelId(), message.getEventId());

        ContentValues values = message.toContentValues(channelId);

        if (programUri == null) {
            // Insert the program.
            // Since we need its Uri, we can't use the batch method.
            Log.d(TAG, "Insert program " + message.getTitle());
            programUri = mContentResolver.insert(TvContract.Programs.CONTENT_URI, values);
            mProgramUriMap.append(message.getEventId(), programUri);
        } else {
            // Update the program
            Log.d(TAG, "Update program " + message.getEventId());

            mPendingProgramOps.add(
                    ContentProviderOperation.newUpdate(programUri)
                            .withValues(values)
                            .build()
            );

        }

        mSeenPrograms.add(message.getEventId());

        // Throttle the batch operation not to cause TransactionTooLargeException.
        if (mPendingProgramOps.size() > 200) {
            flushPendingProgramOps();
        }
    }

    protected void deleteChannels() {
        // Dirty
        int[] existingChannelIds = new int[mChannelUriMap.size()];

        for (int i = 0; i < mChannelUriMap.size(); i++) {
            int key = mChannelUriMap.keyAt(i);
            existingChannelIds[i] = key;
        }

        for (int i = 0; i < existingChannelIds.length; i++) {
            if (!mSeenChannels.contains(existingChannelIds[i])) {
                Log.d(TAG, "Deleting channel " + existingChannelIds[i]);
                Uri channelUri = mChannelUriMap.get(existingChannelIds[i]);
                mChannelUriMap.remove(existingChannelIds[i]);
                mContentResolver.delete(channelUri, null, null);
            }
        }
    }

    protected void deletePrograms() {
        // Dirty
        int[] existingProgramIds = new int[mProgramUriMap.size()];

        for (int i = 0; i < mProgramUriMap.size(); i++) {
            int key = mProgramUriMap.keyAt(i);
            existingProgramIds[i] = key;
        }

        for (int i = 0; i < existingProgramIds.length; i++) {
            if (!mSeenPrograms.contains(existingProgramIds[i])) {
                Log.d(TAG, "Deleting program " + existingProgramIds[i]);
                Uri programUri = mProgramUriMap.get(existingProgramIds[i]);
                mProgramUriMap.remove(existingProgramIds[i]);
                mContentResolver.delete(programUri, null, null);
            }
        }
    }

    protected void flushPendingProgramOps() {
        if (mPendingProgramOps.size() == 0) {
            return;
        }

        try {
            mContext.getContentResolver().applyBatch(Constants.CONTENT_AUTHORITY, mPendingProgramOps);
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Failed to flush pending program operations", e);
            return;
        }
        mPendingProgramOps.clear();
    }

    protected Long getChannelId(int channelId) {
        // TODO: Cache results...
        // TODO: Move to TvContractUtils
        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());

        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID};

        try (Cursor cursor = mContentResolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (cursor.getInt(1) == channelId) {
                    return cursor.getLong(0);
                }
            }
        }

        return null;
    }

    protected Uri getChannelUri(int channelId) {
        // TODO: Cache results...
        // TODO: Move to TvContractUtils
        Long androidChannelId = getChannelId(channelId);

        if (androidChannelId != null) {
            return TvContract.buildChannelUri(androidChannelId);
        }

        return null;
    }

    public SparseArray<Uri> buildChannelUriMap() {
        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Uri> channelMap = new SparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());
        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID};

        try (Cursor cursor = mContentResolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                channelMap.put(originalNetworkId, TvContract.buildChannelUri(rowId));
            }
        }

        return channelMap;
    }

    protected Uri getProgramUri(int channelId, int eventId) {
        // TODO: Cache results...
        // TODO: Move to TvContractUtils
        Long androidChannelId = getChannelId(channelId);

        if (androidChannelId == null) {
            Log.w(TAG, "Failed to fetch programUri, unknown channel");
            return null;
        }

        Uri programsUri = TvContract.buildProgramsUriForChannel(androidChannelId);

        String[] projection = {TvContract.Programs._ID, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA};

        try (Cursor cursor = mContentResolver.query(programsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (cursor.getInt(1) == eventId) {
                    return TvContract.buildProgramUri(cursor.getLong(0));
                }
            }
        }

        return null;
    }

    public SparseArray<Uri> buildProgramUriMap() {
        // Create a map from event id to program row ID for existing programs.
        SparseArray<Uri> programMap = new SparseArray<>();

        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());

        String[] channelsProjection = {TvContract.Channels._ID};
        try (Cursor cursor = mContentResolver.query(channelsUri, channelsProjection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                SparseArray<Uri> channelPrgramMap = buildProgramUriMap(TvContract.buildChannelUri(cursor.getLong(0)));
                for (int i = 0; i < channelPrgramMap.size(); i++) {
                    int key = channelPrgramMap.keyAt(i);
                    Uri value = channelPrgramMap.valueAt(i);
                    programMap.put(key, value);
                }
            }
        }

        return programMap;
    }

    public SparseArray<Uri> buildProgramUriMap(Uri channelUri) {
        // Create a map from event id to program row ID for existing programs.
        SparseArray<Uri> programMap = new SparseArray<>();

        Uri programsUri = TvContract.buildProgramsUriForChannel(channelUri);
        String[] projection = {TvContract.Programs._ID, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA};

        try (Cursor cursor = mContentResolver.query(programsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int tvhEventId = cursor.getInt(1);
                programMap.put(tvhEventId, TvContract.buildChannelUri(rowId));
            }
        }

        return programMap;
    }
}
