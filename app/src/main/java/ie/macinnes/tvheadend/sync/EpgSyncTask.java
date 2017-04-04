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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteFullException;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import ie.macinnes.htsp.HtspFileInputStream;
import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.tasks.Authenticator;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.DvbMappings;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.TvContractUtils;

public class EpgSyncTask implements HtspMessage.Listener, Authenticator.Listener {
    private static final String TAG = EpgSyncTask.class.getSimpleName();
    private static final Set<String> HANDLED_METHODS = new HashSet<>(Arrays.asList(new String[]{
            "channelAdd", "channelUpdate", "channelDelete",
            "eventAdd", "eventUpdate", "eventDelete",
            "initialSyncCompleted",
    }));
    private static final boolean IS_BRAVIA = Build.MODEL.contains("BRAVIA");

    private static final String CHANNEL_ID_KEY = "channelId";
    private static final String CHANNEL_NUMBER_KEY = "channelNumber";
    private static final String CHANNEL_NUMBER_MINOR_KEY = "channelNumberMinor";
    private static final String CHANNEL_NAME_KEY = "channelName";
    private static final String CHANNEL_ICON_KEY = "channelIcon";

    private static final String PROGRAM_DESCRIPTION_KEY = "description";
    private static final String PROGRAM_SUMMARY_KEY = "summary";
    private static final String PROGRAM_TITLE_KEY = "title";
    private static final String PROGRAM_EPISODE_TITLE_KEY = "subtitle";
    private static final String PROGRAM_CONTENT_TYPE_KEY = "contentType";
    private static final String PROGRAM_AGE_RATING_KEY = "ageRating";
    private static final String PROGRAM_START_TIME_KEY = "start";
    private static final String PROGRAM_FINISH_TIME_KEY = "stop";
    private static final String PROGRAM_SEASON_NUMBER_KEY = "seasonNumber";
    private static final String PROGRAM_EPISODE_NUMBER_KEY = "episodeNumber";
    private static final String PROGRAM_IMAGE = "image";

    private static final String EVENT_ID_KEY = "eventId";

    private static final int TWO_HOURS = 2*60*60;

    /**
     * A listener for EpgSync events
     */
    public interface Listener {
        /**
         * Returns the Handler on which to execute the callback.
         *
         * @return Handler, or null.
         */
        Handler getHandler();

        /**
         * Called when the initial sync has completed
         */
        void onInitialSyncCompleted();
    }

    private final Context mContext;
    private final HtspMessage.Dispatcher mDispatcher;
    private boolean mQuickSync = false;

    private final SharedPreferences mSharedPreferences;
    private final ContentResolver mContentResolver;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private final Set<Listener> mListeners = new CopyOnWriteArraySet<>();

    private boolean mInitialSyncCompleted = false;

    private final SparseArray<Uri> mChannelUriMap;
    private final SparseArray<Uri> mProgramUriMap;

    private final ArrayList<PendingChannelAddUpdate> mPendingChannelOps = new ArrayList<>();
    private final ArrayList<PendingEventAddUpdate> mPendingProgramOps = new ArrayList<>();

    private final Queue<PendingChannelLogoFetch> mPendingChannelLogoFetches = new ConcurrentLinkedQueue<>();
    private final byte[] mImageBytes = new byte[102400];

    private Set<Integer> mSeenChannels = new HashSet<>();
    private Set<Integer> mSeenPrograms = new HashSet<>();

    private final class PendingChannelAddUpdate {
        public int channelId;
        public int channelNumber;
        public ContentProviderOperation operation;

        public PendingChannelAddUpdate(int channelId, int channelNumber, ContentProviderOperation operation) {
            this.channelId = channelId;
            this.channelNumber = channelNumber;
            this.operation = operation;
        }
    }

    private final class PendingChannelLogoFetch {
        public int channelId;
        public Uri logoUri;

        public PendingChannelLogoFetch(int channelId, Uri logoUri) {
            this.channelId = channelId;
            this.logoUri = logoUri;
        }
    }

    private final class PendingEventAddUpdate {
        public int eventId;
        public ContentProviderOperation operation;

        public PendingEventAddUpdate(int eventId, ContentProviderOperation operation) {
            this.eventId = eventId;
            this.operation = operation;
        }
    }

    public EpgSyncTask(Context context, @NonNull HtspMessage.Dispatcher dispatcher, boolean quickSync) {
        this(context, dispatcher);

        mQuickSync = quickSync;
    }

    public EpgSyncTask(Context context, @NonNull HtspMessage.Dispatcher dispatcher) {
        mContext = context;
        mDispatcher = dispatcher;

        mSharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        mContentResolver = context.getContentResolver();

        mHandlerThread = new HandlerThread("EpgSyncService Handler Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mChannelUriMap = TvContractUtils.buildChannelUriMap(context);
        mProgramUriMap = TvContractUtils.buildProgramUriMap(context);
    }

    public void addEpgSyncListener(Listener listener) {
        if (mListeners.contains(listener)) {
            Log.w(TAG, "Attempted to add duplicate epg sync listener");
            return;
        }
        mListeners.add(listener);
    }

    public void removeEpgSyncListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            Log.w(TAG, "Attempted to remove non existing epg sync listener");
            return;
        }
        mListeners.remove(listener);
    }

    // Authenticator.Listener Methods
    @Override
    public void onAuthenticationStateChange(@NonNull Authenticator.State state) {
        if (state == Authenticator.State.AUTHENTICATED) {
            long epgMaxTime = Long.parseLong(
                    mSharedPreferences.getString(
                            Constants.KEY_EPG_MAX_TIME,
                            mContext.getResources().getString(R.string.pref_default_epg_max_time)
                    )
            );
            final boolean lastUpdateEnabled = mSharedPreferences.getBoolean(
                    Constants.KEY_EPG_LAST_UPDATE_ENABLED,
                    mContext.getResources().getBoolean(R.bool.pref_default_epg_last_update_enabled)
            );

            Log.i(TAG, "Enabling Async Metadata: maxTime: " + epgMaxTime + ", quickSync: " + mQuickSync);

            // Reset the InitialSyncCompleted flag
            mInitialSyncCompleted = false;

            HtspMessage enableAsyncMetadataRequest = new HtspMessage();

            enableAsyncMetadataRequest.put("method", "enableAsyncMetadata");
            enableAsyncMetadataRequest.put("epg", 1);

            if (mQuickSync) {
                // Quick sync ignores the epg time preference, and syncs 2 hours of data
                epgMaxTime = TWO_HOURS;
            }

            epgMaxTime = epgMaxTime + (System.currentTimeMillis() / 1000L);
            enableAsyncMetadataRequest.put("epgMaxTime", epgMaxTime);

            if (lastUpdateEnabled) {
                final long lastUpdate = mSharedPreferences.getLong(Constants.KEY_EPG_LAST_UPDATE, 0);
                enableAsyncMetadataRequest.put("lastUpdate", lastUpdate);
                Log.d(TAG, "Setting lastUpdate field to " + lastUpdate);
            } else {
                Log.d(TAG, "Skipping lastUpdate field, disabled by preference");
            }

            try {
                mDispatcher.sendMessage(enableAsyncMetadataRequest);
            } catch (HtspNotConnectedException e) {
                Log.d(TAG, "Failed to enable async metadata, HTSP not connected", e);
                return;
            }
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
                case "channelAdd":
                case "channelUpdate":
                    handleChannelAddUpdate(message);
                    storeLastUpdate();
                    break;
                case "channelDelete":
                    // Do Something
                    break;
                case "eventAdd":
                case "eventUpdate":
                    handleEventAddUpdate(message);
                    storeLastUpdate();
                    break;
                case "eventDelete":
                    // Do Something
                    break;
                case "initialSyncCompleted":
                    handleInitialSyncCompleted(message);
                    break;
                default:
                    throw new RuntimeException("Unknown message method: " + method);
            }
        }
    }

    // Internal Methods
    private void storeLastUpdate() {
        long unixTime = System.currentTimeMillis() / 1000L;

        mSharedPreferences.edit().putLong(Constants.KEY_EPG_LAST_UPDATE, unixTime).apply();
    }

    private ContentValues channelToContentValues(@NonNull HtspMessage message) {
        ContentValues values = new ContentValues();

        values.put(TvContract.Channels.COLUMN_INPUT_ID, TvContractUtils.getInputId());
        values.put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER);
        values.put(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID, message.getInteger(CHANNEL_ID_KEY));

        if (message.containsKey(CHANNEL_NUMBER_KEY) && message.containsKey(CHANNEL_NUMBER_MINOR_KEY)) {
            final int channelNumber = message.getInteger(CHANNEL_NUMBER_KEY);
            final int channelNumberMinor = message.getInteger(CHANNEL_NUMBER_MINOR_KEY);
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, channelNumber + "." + channelNumberMinor);
        } else if (message.containsKey(CHANNEL_NUMBER_KEY)) {
            final int channelNumber = message.getInteger(CHANNEL_NUMBER_KEY);
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, String.valueOf(channelNumber));
        }

        if (message.containsKey(CHANNEL_NAME_KEY)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, message.getString(CHANNEL_NAME_KEY));
        }

        // TODO
//        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, accountName);

        return values;
    }

    private void handleChannelAddUpdate(@NonNull HtspMessage message) {
        final int channelId = message.getInteger(CHANNEL_ID_KEY);
        final ContentValues values = channelToContentValues(message);
        final Uri channelUri = TvContractUtils.getChannelUri(mContext, channelId);

        if (channelUri == null) {
            // Insert the channel
            if (Constants.DEBUG)
                Log.v(TAG, "Insert channel " + channelId);
            final int channelNumber = message.getInteger(CHANNEL_NUMBER_KEY);
            mPendingChannelOps.add(new PendingChannelAddUpdate(
                    channelId, channelNumber,
                    ContentProviderOperation.newInsert(TvContract.Channels.CONTENT_URI)
                        .withValues(values)
                        .build()
            ));
        } else {
            // Update the channel
            if (Constants.DEBUG)
                Log.v(TAG, "Update channel " + channelId);
            mPendingChannelOps.add(new PendingChannelAddUpdate(
                    channelId, -1,
                    ContentProviderOperation.newUpdate(channelUri)
                            .withValues(values)
                            .build()
            ));
        }

        if (mInitialSyncCompleted) {
            flushPendingChannelOps();
        } else if (!IS_BRAVIA && mPendingChannelOps.size() >= 100) {
            // Throttle the batch operation not to cause TransactionTooLargeException. If the initial
            // sync has already completed, flush for every message. Additionally, we have no choice
            // on a Sony set but to not flush until we have all the channels, as sony's EPG view
            // is buggy.
            flushPendingChannelOps();
        }

        if (message.containsKey(CHANNEL_ICON_KEY)) {
            mPendingChannelLogoFetches.add(new PendingChannelLogoFetch(channelId, Uri.parse(message.getString(CHANNEL_ICON_KEY))));
        }

        mSeenChannels.add(channelId);
    }

    private void flushPendingChannelOps() {
        if (mPendingChannelOps.size() == 0) {
            return;
        }

        Log.d(TAG, "Flushing " + mPendingChannelOps.size() + " channel operations");

        if (IS_BRAVIA) {
            // Sort the Pending Add/Updates by their channel numbers as Sony fails to do this in
            // their EPG view.
            Collections.sort(mPendingChannelOps, new Comparator<PendingChannelAddUpdate>() {
                @Override
                public int compare(PendingChannelAddUpdate o1, PendingChannelAddUpdate o2) {
                    if (o1.channelNumber == -1 || o2.channelNumber == -1) {
                        // One of the events is a channelUpdate, no need (or ability) to sort it
                        return 0;
                    }
                    if (o1.channelNumber > o2.channelNumber) {
                        return 1;
                    } else if (o1.channelNumber == o2.channelNumber) {
                        return 0;
                    }

                    return -1;
                }
            });
        }

        // Build out an ArrayList of Operations needed for applyBatch()
        ArrayList<ContentProviderOperation> operations = new ArrayList<>(mPendingChannelOps.size());
        for (PendingChannelAddUpdate pcau : mPendingChannelOps) {
            operations.add(pcau.operation);
        }

        // Apply the batch of Operations
        ContentProviderResult[] results;
        try {
            results = mContext.getContentResolver().applyBatch(
                    Constants.CONTENT_AUTHORITY, operations);
        } catch (RemoteException | OperationApplicationException | SQLiteFullException e) {
            Log.e(TAG, "Failed to flush pending channel operations", e);
            return;
        }

        if (operations.size() != results.length) {
            Log.e(TAG, "Failed to flush pending channels, discarding and moving on, batch size " +
                       "does not match resultset size");

            // Reset the pending operations list
            mPendingChannelOps.clear();
            return;
        }

        // Update the Channel Uri Map based on the results
        for (int i = 0; i < mPendingChannelOps.size(); i++) {
            final int channelId = mPendingChannelOps.get(i).channelId;
            final ContentProviderResult result = results[i];

            mChannelUriMap.put(channelId, result.uri);
        }

        // Finally, reset the pending operations list
        mPendingChannelOps.clear();
    }

    private void flushPendingChannelLogoFetches() {
        if (mPendingChannelLogoFetches.size() == 0) {
            return;
        }

        Log.d(TAG, "Flushing " + mPendingChannelLogoFetches.size() + " channel logo fetches");

        for (PendingChannelLogoFetch pendingChannelLogoFetch : mPendingChannelLogoFetches) {
            final int channelId = pendingChannelLogoFetch.channelId;
            final long androidChannelId = TvContractUtils.getChannelId(mContext, channelId);

            if (androidChannelId == TvContractUtils.INVALID_CHANNEL_ID) {
                Log.e(TAG, "Failed to final channel in android DB, channel ID: " + channelId);
                continue;
            }

            final Uri channelLogoSourceUri = pendingChannelLogoFetch.logoUri;
            final Uri channelLogoDestUri = TvContract.buildChannelLogoUri(androidChannelId);

            InputStream is = null;
            OutputStream os = null;

            try {
                final String logoScheme = channelLogoSourceUri.getScheme();
                if (logoScheme != null && (logoScheme.equalsIgnoreCase("http") || logoScheme.equalsIgnoreCase("https"))) {
                    is = new URL(channelLogoSourceUri.toString()).openStream();
                } else {
                    is = new HtspFileInputStream(mDispatcher, channelLogoSourceUri.getPath());
                }

                os = mContentResolver.openOutputStream(channelLogoDestUri);

                int read;
                int totalRead = 0;

                while ((read = is.read(mImageBytes)) != -1) {
                    os.write(mImageBytes, 0, read);
                    totalRead += read;
                }

                Log.d(TAG, "Successfully fetched logo from " + channelLogoSourceUri + " to " + channelLogoDestUri + " (" + totalRead + " bytes)");

            } catch (IOException e) {
                Log.e(TAG, "Failed to fetch logo from " + channelLogoSourceUri + " to " + channelLogoDestUri, e);

            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore...
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        // Ignore...
                    }
                }
            }

            mPendingChannelLogoFetches.remove(channelId);
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
                if (Constants.DEBUG)
                    Log.d(TAG, "Deleting channel " + existingChannelIds[i]);
                Uri channelUri = mChannelUriMap.get(existingChannelIds[i]);
                mChannelUriMap.remove(existingChannelIds[i]);
                mContentResolver.delete(channelUri, null, null);
            }
        }
    }

    private ContentValues eventToContentValues(@NonNull HtspMessage message) {
        ContentValues values = new ContentValues();

        values.put(TvContract.Programs.COLUMN_CHANNEL_ID, TvContractUtils.getChannelId(mContext, message.getInteger(CHANNEL_ID_KEY)));
        values.put(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA, String.valueOf(message.getInteger(EVENT_ID_KEY)));

        // COLUMN_TITLE, COLUMN_EPISODE_TITLE, and COLUMN_SHORT_DESCRIPTION are used in the
        // Live Channels app EPG Grid. COLUMN_LONG_DESCRIPTION appears unused.
        // On Sony TVs, COLUMN_LONG_DESCRIPTION is used for the "more info" display.

        if (message.containsKey(PROGRAM_TITLE_KEY)) {
            // The title of this TV program.
            values.put(TvContract.Programs.COLUMN_TITLE, message.getString(PROGRAM_TITLE_KEY));
        }

        if (message.containsKey(PROGRAM_EPISODE_TITLE_KEY)) {
            // The episode title of this TV program for episodic TV shows.
            values.put(TvContract.Programs.COLUMN_EPISODE_TITLE, message.getString(PROGRAM_EPISODE_TITLE_KEY));
        }

        if (message.containsKey(PROGRAM_SUMMARY_KEY) && message.containsKey(PROGRAM_DESCRIPTION_KEY)) {
            // If we have both summary and description... use them both
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, message.getString(PROGRAM_SUMMARY_KEY));
            values.put(TvContract.Programs.COLUMN_LONG_DESCRIPTION, message.getString(PROGRAM_DESCRIPTION_KEY));

        } else if (message.containsKey(PROGRAM_SUMMARY_KEY) && !message.containsKey(PROGRAM_DESCRIPTION_KEY)) {
            // If we have only summary, use it.
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, message.getString(PROGRAM_SUMMARY_KEY));

        } else if (!message.containsKey(PROGRAM_SUMMARY_KEY) && message.containsKey(PROGRAM_DESCRIPTION_KEY)) {
            // If we have only description, use it.
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, message.getString(PROGRAM_DESCRIPTION_KEY));
        }

        if (message.containsKey(PROGRAM_CONTENT_TYPE_KEY)) {
            values.put(TvContract.Programs.COLUMN_CANONICAL_GENRE,
                    DvbMappings.ProgramGenre.get(message.getInteger(PROGRAM_CONTENT_TYPE_KEY)));
        }

        if (message.containsKey(PROGRAM_AGE_RATING_KEY)) {
            final int ageRating = message.getInteger(PROGRAM_AGE_RATING_KEY);
            if (ageRating >= 4 && ageRating <= 18) {
                TvContentRating rating = TvContentRating.createRating("com.android.tv", "DVB", "DVB_" + ageRating);
                values.put(TvContract.Programs.COLUMN_CONTENT_RATING, rating.flattenToString());
            }
        }

        if (message.containsKey(PROGRAM_START_TIME_KEY)) {
            values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, message.getLong(PROGRAM_START_TIME_KEY) * 1000);
        }

        if (message.containsKey(PROGRAM_FINISH_TIME_KEY)) {
            values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, message.getLong(PROGRAM_FINISH_TIME_KEY) * 1000);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (message.containsKey(PROGRAM_SEASON_NUMBER_KEY)) {
                values.put(TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER, message.getInteger(PROGRAM_SEASON_NUMBER_KEY));
            }

            if (message.containsKey(PROGRAM_EPISODE_NUMBER_KEY)) {
                values.put(TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER, message.getInteger(PROGRAM_EPISODE_NUMBER_KEY));
            }
        } else {
            if (message.containsKey(PROGRAM_SEASON_NUMBER_KEY)) {
                values.put(TvContract.Programs.COLUMN_SEASON_NUMBER, message.getInteger(PROGRAM_SEASON_NUMBER_KEY));
            }

            if (message.containsKey(PROGRAM_EPISODE_NUMBER_KEY)) {
                values.put(TvContract.Programs.COLUMN_EPISODE_NUMBER, message.getInteger(PROGRAM_EPISODE_NUMBER_KEY));
            }
        }

        // TODO: Looking this up for every event we process, we don't need to.
        final boolean defaultPosterArtEnabled = mSharedPreferences.getBoolean(
                Constants.KEY_EPG_DEFAULT_POSTER_ART_ENABLED,
                mContext.getResources().getBoolean(R.bool.pref_default_epg_default_poster_art_enabled)
        );

        if (message.containsKey(PROGRAM_IMAGE)) {
            values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, message.getString(PROGRAM_IMAGE));
        } else if(defaultPosterArtEnabled) {
            values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, "android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.default_event_icon);
        }

        return values;
    }

    private void handleEventAddUpdate(@NonNull HtspMessage message) {
        // Ensure we wrap up any pending channel operations before moving onto events. This is no-op
        // once there are no pending operations
        flushPendingChannelOps();

        final int channelId = message.getInteger(CHANNEL_ID_KEY);
        final int eventId = message.getInteger(EVENT_ID_KEY);
        final ContentValues values = eventToContentValues(message);
        final Uri eventUri = TvContractUtils.getProgramUri(mContext, channelId, eventId);

        if (eventUri == null) {
            // Insert the event
            if (Constants.DEBUG)
                Log.v(TAG, "Insert event " + eventId + " on channel " + channelId);
            mPendingProgramOps.add(new PendingEventAddUpdate(
                    eventId,
                    ContentProviderOperation.newInsert(TvContract.Programs.CONTENT_URI)
                            .withValues(values)
                            .build()
            ));
        } else {
            // Update the event
            if (Constants.DEBUG)
                Log.v(TAG, "Update event " + eventId + " on channel " + channelId);
            mPendingProgramOps.add(new PendingEventAddUpdate(
                    eventId,
                    ContentProviderOperation.newUpdate(eventUri)
                            .withValues(values)
                            .build()
            ));
        }

        // Throttle the batch operation not to cause TransactionTooLargeException. If the initial
        // sync has already completed, flush for every message.
        if (mInitialSyncCompleted || mPendingProgramOps.size() >= 100) {
            flushPendingEventOps();
        }

        mSeenPrograms.add(eventId);
    }

    private void flushPendingEventOps() {
        if (mPendingProgramOps.size() == 0) {
            return;
        }

        Log.d(TAG, "Flushing " + mPendingProgramOps.size() + " event operations");

        // Build out an ArrayList of Operations needed for applyBatch()
        ArrayList<ContentProviderOperation> operations = new ArrayList<>(mPendingProgramOps.size());
        for (PendingEventAddUpdate peau : mPendingProgramOps) {
            operations.add(peau.operation);
        }

        // Apply the batch of Operations
        ContentProviderResult[] results;

        try {
            results = mContext.getContentResolver().applyBatch(
                    Constants.CONTENT_AUTHORITY, operations);
        } catch (RemoteException | OperationApplicationException | SQLiteFullException e) {
            Log.e(TAG, "Failed to flush pending event operations", e);
            return;
        }

        if (operations.size() != results.length) {
            Log.e(TAG, "Failed to flush pending events, discarding and moving on, batch size " +
                       "does not match resultset size");

            // Reset the pending operations list
            mPendingProgramOps.clear();
            return;
        }

        // Update the Event Uri Map based on the results
        for (int i = 0; i < mPendingProgramOps.size(); i++) {
            final int eventId = mPendingProgramOps.get(i).eventId;
            final ContentProviderResult result = results[i];

            mProgramUriMap.put(eventId, result.uri);
        }

        // Finally, reset the pending operations list
        mPendingProgramOps.clear();
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
                if (Constants.DEBUG)
                    Log.d(TAG, "Deleting program " + existingProgramIds[i]);
                Uri programUri = mProgramUriMap.get(existingProgramIds[i]);
                mProgramUriMap.remove(existingProgramIds[i]);
                mContentResolver.delete(programUri, null, null);
            }
        }
    }

    private void handleInitialSyncCompleted(@NonNull HtspMessage message) {
        // Ensure we wrap up any pending channel operations. This is no-op once there are no pending
        // operations. This should only be needed when there were no events provided at all.
        flushPendingChannelOps();

        // Ensure we wrap up any pending event operations. This is no-op once there are no pending
        // operations
        flushPendingEventOps();

        // Clear out any stale date
        deleteChannels();
        deletePrograms();

        // Fetch all the channel logos
        flushPendingChannelLogoFetches();

        Log.i(TAG, "Initial sync completed");
        mInitialSyncCompleted = true;

        // Let our listeners know
        for (final Listener listener : mListeners) {
            Handler handler = listener.getHandler();
            if (handler == null) {
                listener.onInitialSyncCompleted();
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onInitialSyncCompleted();
                    }
                });
            }
        }
    }
}
