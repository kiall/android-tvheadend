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
package ie.macinnes.tvheadend.utils;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.model.Program;
import ie.macinnes.tvheadend.model.ProgramList;

public class TvContractUtils {
    private static final String TAG = TvContractUtils.class.getName();

    public static Channel getChannelFromChannelUri(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();

        // TODO: Handle when more than 1, or 0 results come back
        try (Cursor cursor = resolver.query(channelUri, null, null,null, null)) {
            return ChannelList.fromCursor(cursor).get(0);
        }
    }

    public static ChannelList getChannels(Context context, String inputId, String[] projection) {
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            return ChannelList.fromCursor(cursor);
        }
    }


    public static void removeChannels(Context context, String inputId) {
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);

        ContentResolver resolver = context.getContentResolver();

        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                Log.d(TAG, "Deleting channel: " + rowId);
                resolver.delete(TvContract.buildChannelUri(rowId), null, null);
            }
        }
    }

    public static void updateChannels(Context context, String inputId, ChannelList channelList) {
        Log.d(TAG, "Updating channels for inputId: " + inputId);

        Collections.sort(channelList);

        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Long> channelMap = new SparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};
        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                channelMap.put(originalNetworkId, rowId);
            }
        }

        // If a channel exists, update it. If not, insert a new one.
        ContentValues values;
        Map<Uri, String> logos = new HashMap<>();

        for (Channel channel : channelList) {
            values = channel.toContentValues();

            Long rowId = channelMap.get(channel.getOriginalNetworkId());

            Uri uri;
            if (rowId == null) {
                Log.d(TAG, "Adding channel: " + channel.toString());
                uri = resolver.insert(TvContract.Channels.CONTENT_URI, values);
            } else {
                Log.d(TAG, "Updating channel: " + channel.toString());
                uri = TvContract.buildChannelUri(rowId);
                resolver.update(uri, values, null, null);
                channelMap.remove(channel.getOriginalNetworkId());
            }

            // Update the channel icon
            if (channel.getIconUri() != null && !TextUtils.isEmpty(channel.getIconUri())) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.getIconUri());
            }
        }

        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }

        // Deletes channels which don't exist in the new feed.
        int size = channelMap.size();
        for (int i = 0; i < size; ++i) {
            Long rowId = channelMap.valueAt(i);
            Log.d(TAG, "Deleting channel: " + rowId);
            resolver.delete(TvContract.buildChannelUri(rowId), null, null);
        }
    }

    public static ProgramList getPrograms(Context context, Channel channel) {
        Uri channelUri = TvContract.buildChannelUri(channel.getId());
        Uri programsUri = TvContract.buildProgramsUriForChannel(channelUri);

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(programsUri, null, null, null, null)) {
            return ProgramList.fromCursor(cursor);
        }
    }

    public static void updateEvents(Context context, Channel channel, ProgramList newProgramList) {
        Log.d(TAG, "Updating events for channel: " + channel.toString() + ". Have " + newProgramList.size() + " events.");

        ProgramList oldProgramList = getPrograms(context, channel);

        int oldProgramsIndex = 0;
        int newProgramsIndex = 0;
        final int oldProgramsCount = oldProgramList.size();
        final int newProgramsCount = newProgramList.size();

        // Compare the new programs with old programs one by one and update/delete the old one
        // or insert new program if there is no matching program in the database.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        while (newProgramsIndex < newProgramsCount) {
            Program oldProgram = oldProgramsIndex < oldProgramsCount
                    ? oldProgramList.get(oldProgramsIndex) : null;
            Program newProgram = newProgramList.get(newProgramsIndex);

            boolean addNewProgram = false;
            if (oldProgram != null) {
                if (oldProgram.equals(newProgram)) {
                    // Exact match. No need to update. Move on to the next programs.
                    oldProgramsIndex++;
                    newProgramsIndex++;

                } else if (programEventIdMatches(oldProgram, newProgram)) {
                    // Partial match. Update the old program with the new one.
                    // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                    // could be application specific settings which belong to the old program.
                    ops.add(ContentProviderOperation.newUpdate(
                            TvContract.buildProgramUri(oldProgram.getProgramId()))
                            .withValues(newProgram.toContentValues())
                            .build());
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else {
                    // No match. The new program does not match any of the old programs. Insert
                    // it as a new program.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
            } else {
                // No old programs. Just insert new programs.
                addNewProgram = true;
                newProgramsIndex++;
            }

            if (addNewProgram) {
                ops.add(ContentProviderOperation
                        .newInsert(TvContract.Programs.CONTENT_URI)
                        .withValues(newProgram.toContentValues())
                        .build());
            }

            // Throttle the batch operation not to cause TransactionTooLargeException.
            if (ops.size() > 100
                    || newProgramsIndex >= newProgramsCount) {
                try {
                    context.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to insert programs.", e);
                    return;
                }
                ops.clear();
            }
        }
    }

    private static boolean programEventIdMatches(Program oldProgram, Program newProgram) {
        // TODO: Handle null's etc...
        return oldProgram.getInternalProviderData().getEventId() ==
               newProgram.getInternalProviderData().getEventId();
    }

    public static void insertUrl(Context context, Uri contentUri, URL sourceUrl) {
        Log.d(TAG, "Inserting logo " + sourceUrl + " to " + contentUri);

        InputStream is = null;
        OutputStream os = null;

        try {
            is = sourceUrl.openStream();
            os = context.getContentResolver().openOutputStream(contentUri);
            copy(is, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to copy " + sourceUrl + "  to " + contentUri, ioe);
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
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    public static class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
        private final Context mContext;;

        InsertLogosTask(Context context) {
            mContext = context;

        }

        @Override
        public Void doInBackground(Map<Uri, String>... logosList) {
            for (Map<Uri, String> logos : logosList) {
                for (Uri uri : logos.keySet()) {
                    try {
                        insertUrl(mContext, uri, new URL(logos.get(uri)));
                    } catch (MalformedURLException e) {
                        Log.e(TAG, "Failed to load icon " + logos.get(uri), e);
                    }
                }
            }
            return null;
        }
    }
}
